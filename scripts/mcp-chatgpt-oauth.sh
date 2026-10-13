#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${MCP_OAUTH_BACKEND_PORT:-8086}"
PROXY_PORT="${MCP_OAUTH_PROXY_PORT:-8087}"
CLIENT_ID="${MCP_OAUTH_CLIENT_ID:-local-mcp-client}"
REDIRECT_URI="${MCP_OAUTH_REDIRECT_URI:-http://127.0.0.1:9876/callback}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-tenancy_platform}"
DB_USER="${DB_USER:-postgres}"

for command in mvn node cloudflared openssl curl python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing command: $command" >&2; exit 1; }
done

LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mcp-oauth.XXXXXX")"
BACKEND_PID=""
PROXY_PID=""
TUNNEL_PID=""
cleanup() {
  trap - EXIT INT TERM
  for pid in "$TUNNEL_PID" "$PROXY_PID" "$BACKEND_PID"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  for pid in "$TUNNEL_PID" "$PROXY_PID" "$BACKEND_PID"; do
    [[ -n "$pid" ]] && wait "$pid" 2>/dev/null || true
  done
  rm -rf "$LOG_DIR"
}
trap cleanup EXIT INT TERM

cd "$ROOT_DIR"
cloudflared tunnel --url "http://127.0.0.1:$PROXY_PORT" --no-autoupdate >"$LOG_DIR/tunnel.log" 2>&1 &
TUNNEL_PID=$!
TUNNEL_URL=""
for _ in $(seq 1 60); do
  TUNNEL_URL="$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG_DIR/tunnel.log" | head -n 1 || true)"
  [[ -n "$TUNNEL_URL" ]] && break
  kill -0 "$TUNNEL_PID" 2>/dev/null || { cat "$LOG_DIR/tunnel.log" >&2; exit 1; }
  sleep 1
done
[[ -n "$TUNNEL_URL" ]] || { echo "Cloudflare tunnel did not provide a URL" >&2; exit 1; }
PUBLIC_HOST="${TUNNEL_URL#https://}"
FIRST_PASSWORD="$(openssl rand -hex 20)"
SECOND_PASSWORD="$(openssl rand -hex 20)"

SERVER_ADDRESS=127.0.0.1 \
SERVER_PORT="$BACKEND_PORT" \
SERVER_SERVLET_SESSION_COOKIE_SECURE=true \
SERVER_SERVLET_SESSION_COOKIE_SAME_SITE=Lax \
SPRING_PROFILES_ACTIVE=oauth-local \
APP_SEED_ENABLED=false \
DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
DB_USER="$DB_USER" \
MCP_PUBLIC_BASE_URL="$TUNNEL_URL" \
MCP_ISSUER_URI="$TUNNEL_URL" \
MCP_RESOURCE_URI="$TUNNEL_URL/mcp" \
MCP_ALLOWED_HOSTS="127.0.0.1:$BACKEND_PORT,$PUBLIC_HOST" \
MCP_ALLOWED_ORIGINS="https://chatgpt.com,https://chat.openai.com" \
MCP_OAUTH_CLIENT_ID="$CLIENT_ID" \
MCP_OAUTH_REDIRECT_URI="$REDIRECT_URI" \
MCP_OAUTH_DEMO_USER1_PASSWORD="$FIRST_PASSWORD" \
MCP_OAUTH_DEMO_USER2_PASSWORD="$SECOND_PASSWORD" \
mvn -q spring-boot:run >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "Avvio server OAuth locale su 127.0.0.1:$BACKEND_PORT..."
ready=false
for _ in $(seq 1 180); do
  status="$(curl --silent --max-time 2 -o /dev/null -w '%{http_code}' \
    "http://127.0.0.1:$BACKEND_PORT/.well-known/oauth-authorization-server" || true)"
  [[ "$status" == "200" ]] && { ready=true; break; }
  kill -0 "$BACKEND_PID" 2>/dev/null || { cat "$LOG_DIR/backend.log" >&2; exit 1; }
  sleep 1
done
[[ "$ready" == true ]] || { cat "$LOG_DIR/backend.log" >&2; exit 1; }

MCP_OAUTH_PUBLIC_HOST="$PUBLIC_HOST" \
MCP_OAUTH_BACKEND_URL="http://127.0.0.1:$BACKEND_PORT" \
MCP_OAUTH_PROXY_PORT="$PROXY_PORT" \
node scripts/mcp-oauth-proxy.mjs >"$LOG_DIR/proxy.log" 2>&1 &
PROXY_PID=$!
for _ in $(seq 1 30); do
  grep -q "listening on 127.0.0.1:$PROXY_PORT" "$LOG_DIR/proxy.log" && break
  kill -0 "$PROXY_PID" 2>/dev/null || { cat "$LOG_DIR/proxy.log" >&2; exit 1; }
  sleep 1
done

public_ready=false
for _ in $(seq 1 30); do
  metadata="$(curl --silent --max-time 10 "$TUNNEL_URL/.well-known/oauth-authorization-server" || true)"
  if [[ "$metadata" == *"\"issuer\":\"$TUNNEL_URL\""* ]] && \
     [[ "$metadata" == *'"code_challenge_methods_supported":["S256"]'* ]] && \
     [[ "$metadata" == *'"none"'* ]]; then
    public_ready=true
    break
  fi
  kill -0 "$TUNNEL_PID" 2>/dev/null || break
  sleep 2
done
[[ "$public_ready" == true ]] || { echo "Public OAuth metadata is unreachable" >&2; exit 1; }

MCP_OAUTH_TEST_BASE="$TUNNEL_URL" \
MCP_OAUTH_TEST_RESOURCE="$TUNNEL_URL/mcp" \
MCP_OAUTH_TEST_MCP_URL="$TUNNEL_URL/mcp" \
MCP_OAUTH_REDIRECT_URI="$REDIRECT_URI" \
MCP_OAUTH_CLIENT_ID="$CLIENT_ID" \
MCP_OAUTH_TEST_PASSWORD_USER1="$FIRST_PASSWORD" \
MCP_OAUTH_TEST_PASSWORD_USER2="$SECOND_PASSWORD" \
python3 scripts/check-oauth-local.py

echo
echo "URL MCP per ChatGPT: $TUNNEL_URL/mcp"
echo "In ChatGPT seleziona OAuth e Client ID Metadata Document (CIMD); nessun secret."
echo "Client di verifica locale: $CLIENT_ID"
echo "Credenziali temporanee, valide solo finché questo processo rimane aperto:"
echo "  user1@email.com  $FIRST_PASSWORD"
echo "  user2@email.com  $SECOND_PASSWORD"
echo "Non condividere queste password. Ctrl+C arresta tunnel, proxy e server."
while kill -0 "$TUNNEL_PID" 2>/dev/null; do
  if grep -q 'Unauthorized: Tunnel not found' "$LOG_DIR/tunnel.log"; then
    echo "Quick Tunnel Cloudflare scaduto" >&2
    exit 1
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null || ! kill -0 "$PROXY_PID" 2>/dev/null; then
    echo "Backend o proxy terminato" >&2
    exit 1
  fi
  sleep 5
done
wait "$TUNNEL_PID"
