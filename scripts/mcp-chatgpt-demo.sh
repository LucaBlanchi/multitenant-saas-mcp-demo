#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${MCP_DEMO_BACKEND_PORT:-8082}"
PROXY_PORT="${MCP_DEMO_PROXY_PORT:-8083}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-tenancy_platform}"
DB_USER="${DB_USER:-postgres}"
DEMO_USER="${MCP_DEMO_USER_EMAIL:-user1@email.com}"

for command in mvn node cloudflared openssl curl python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

MCP_DEMO_ACCESS_KEY="$(openssl rand -hex 32)"
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mcp-chatgpt-demo.XXXXXX")"
BACKEND_PID=""
PROXY_PID=""
TUNNEL_PID=""

cleanup() {
  trap - EXIT INT TERM
  for pid in "$TUNNEL_PID" "$PROXY_PID" "$BACKEND_PID"; do
    if [[ -n "$pid" ]]; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  for pid in "$TUNNEL_PID" "$PROXY_PID" "$BACKEND_PID"; do
    if [[ -n "$pid" ]]; then
      wait "$pid" 2>/dev/null || true
    fi
  done
  rm -rf "$LOG_DIR"
}
trap cleanup EXIT INT TERM

cd "$ROOT_DIR"
SERVER_ADDRESS=127.0.0.1 \
SERVER_PORT="$BACKEND_PORT" \
MCP_ALLOWED_HOSTS="127.0.0.1:$BACKEND_PORT" \
MCP_ALLOWED_ORIGINS="https://chatgpt.com,https://chat.openai.com" \
SPRING_PROFILES_ACTIVE=mcp-demo \
MCP_DEMO_ACCESS_KEY="$MCP_DEMO_ACCESS_KEY" \
MCP_DEMO_USER_EMAIL="$DEMO_USER" \
APP_SEED_ENABLED=false \
DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
DB_USER="$DB_USER" \
mvn spring-boot:run >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "Avvio backend MCP demo su 127.0.0.1:$BACKEND_PORT..."
initialize='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"mcp-chatgpt-demo-check","version":"1"}}}'
backend_ready=false
for _ in $(seq 1 180); do
  status="$(curl --silent --max-time 2 -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    --data "$initialize" \
    "http://127.0.0.1:$BACKEND_PORT/mcp?mcp_demo_key=$MCP_DEMO_ACCESS_KEY" || true)"
  if [[ "$status" == "200" ]]; then
    backend_ready=true
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    cat "$LOG_DIR/backend.log" >&2
    echo "Il backend MCP demo non è partito." >&2
    exit 1
  fi
  sleep 1
done
if [[ "$backend_ready" != true ]]; then
  cat "$LOG_DIR/backend.log" >&2
  echo "Timeout in attesa del backend MCP demo." >&2
  exit 1
fi

MCP_CHECK_URL="http://127.0.0.1:$BACKEND_PORT/mcp?mcp_demo_key=$MCP_DEMO_ACCESS_KEY" \
python3 scripts/check-mcp-demo.py

MCP_DEMO_ACCESS_KEY="$MCP_DEMO_ACCESS_KEY" \
MCP_BACKEND_URL="http://127.0.0.1:$BACKEND_PORT" \
MCP_PROXY_PORT="$PROXY_PORT" \
MCP_PROXY_AUDIT="${MCP_PROXY_AUDIT:-false}" \
node scripts/mcp-demo-proxy.mjs >"$LOG_DIR/proxy.log" 2>&1 &
PROXY_PID=$!
for _ in $(seq 1 30); do
  if grep -q "127.0.0.1:$PROXY_PORT" "$LOG_DIR/proxy.log"; then
    break
  fi
  if ! kill -0 "$PROXY_PID" 2>/dev/null; then
    cat "$LOG_DIR/proxy.log" >&2
    echo "Il proxy MCP demo non è partito." >&2
    exit 1
  fi
  sleep 1
done
if ! grep -q "127.0.0.1:$PROXY_PORT" "$LOG_DIR/proxy.log"; then
  cat "$LOG_DIR/proxy.log" >&2
  echo "Timeout in attesa del proxy MCP demo." >&2
  exit 1
fi

cloudflared tunnel --url "http://127.0.0.1:$PROXY_PORT" --no-autoupdate >"$LOG_DIR/tunnel.log" 2>&1 &
TUNNEL_PID=$!
TUNNEL_URL=""
for _ in $(seq 1 60); do
  TUNNEL_URL="$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG_DIR/tunnel.log" | head -n 1 || true)"
  if [[ -n "$TUNNEL_URL" ]]; then
    break
  fi
  if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
    cat "$LOG_DIR/tunnel.log" >&2
    echo "Il tunnel Cloudflare non è partito." >&2
    exit 1
  fi
  sleep 1
done
if [[ -z "$TUNNEL_URL" ]]; then
  cat "$LOG_DIR/tunnel.log" >&2
  echo "Timeout in attesa dell'URL Cloudflare." >&2
  exit 1
fi

echo "Verifico il tunnel dalla rete pubblica..."
public_ready=false
for _ in $(seq 1 30); do
  if MCP_CHECK_DISCOVERY=true MCP_CHECK_URL="$TUNNEL_URL/mcp?mcp_demo_key=$MCP_DEMO_ACCESS_KEY" \
      python3 scripts/check-mcp-demo.py >"$LOG_DIR/public-check.log" 2>&1; then
    public_ready=true
    break
  fi
  if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
    break
  fi
  sleep 2
done
if [[ "$public_ready" != true ]]; then
  cat "$LOG_DIR/public-check.log" >&2
  echo "Il tunnel non è raggiungibile dalla rete pubblica; riavvia il comando." >&2
  exit 1
fi

echo
echo "URL MCP da inserire in ChatGPT:"
echo "$TUNNEL_URL/mcp?mcp_demo_key=$MCP_DEMO_ACCESS_KEY"
echo
echo "Dati demo: $DEMO_USER — nessuna autenticazione MCP nella configurazione ChatGPT."
echo "Tieni aperto questo terminale; Ctrl+C ferma tunnel, proxy e backend demo."
while kill -0 "$TUNNEL_PID" 2>/dev/null; do
  if grep -q 'Unauthorized: Tunnel not found' "$LOG_DIR/tunnel.log"; then
    echo "Il Quick Tunnel Cloudflare è scaduto. Riavvia il comando per un nuovo URL." >&2
    exit 1
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null || ! kill -0 "$PROXY_PID" 2>/dev/null; then
    echo "Backend o proxy demo terminato; il tunnel viene fermato." >&2
    exit 1
  fi
  sleep 5
done
wait "$TUNNEL_PID"
