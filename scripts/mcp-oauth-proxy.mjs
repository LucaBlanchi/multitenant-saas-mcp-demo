import { createServer } from 'node:http';

const backend = new URL(process.env.MCP_OAUTH_BACKEND_URL || 'http://127.0.0.1:8086');
const publicHost = process.env.MCP_OAUTH_PUBLIC_HOST;
const port = Number(process.env.MCP_OAUTH_PROXY_PORT || 8087);
if (!publicHost || !/^[a-z0-9.-]+$/i.test(publicHost)) {
  throw new Error('MCP_OAUTH_PUBLIC_HOST must be a DNS hostname');
}

const routes = new Map([
  ['/mcp', new Set(['POST'])],
  ['/.well-known/oauth-protected-resource', new Set(['GET'])],
  ['/.well-known/oauth-protected-resource/mcp', new Set(['GET'])],
  ['/.well-known/oauth-authorization-server', new Set(['GET'])],
  ['/oauth2/authorize', new Set(['GET', 'POST'])],
  ['/oauth2/token', new Set(['POST'])],
  ['/oauth2/revoke', new Set(['POST'])],
  ['/oauth2/jwks', new Set(['GET'])],
  ['/login', new Set(['GET', 'POST'])],
  ['/default-ui.css', new Set(['GET'])],
]);

const server = createServer(async (request, response) => {
  const url = new URL(request.url || '/', 'http://localhost');
  const allowedMethods = routes.get(url.pathname);
  if (!allowedMethods?.has(request.method)) {
    response.writeHead(404).end();
    return;
  }
  if (request.headers.host !== publicHost && request.headers.host !== `127.0.0.1:${port}`) {
    response.writeHead(403).end();
    return;
  }

  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 1024 * 1024) {
      response.writeHead(413).end();
      return;
    }
    chunks.push(chunk);
  }
  const body = Buffer.concat(chunks);
  if (url.pathname === '/mcp' && request.method === 'POST') {
    let rpc;
    try { rpc = JSON.parse(body.toString()); } catch {}
    if (rpc?.method === 'server/discover') {
      response.writeHead(404, { 'Content-Type': 'application/json' }).end(JSON.stringify({
        jsonrpc: '2.0', id: rpc.id ?? null,
        error: { code: -32601, message: 'Method not found; use the MCP initialize handshake' },
      }));
      return;
    }
  }

  const headers = new Headers({
    Host: backend.host,
    'X-Forwarded-Host': publicHost,
    'X-Forwarded-Proto': 'https',
    'X-Forwarded-Port': '443',
  });
  for (const name of ['accept', 'authorization', 'content-type', 'cookie', 'origin',
    'mcp-protocol-version', 'user-agent']) {
    if (request.headers[name]) headers.set(name, request.headers[name]);
  }
  try {
    const upstream = await fetch(new URL(url.pathname + url.search, backend), {
      method: request.method, headers, body: request.method === 'GET' ? undefined : body,
      redirect: 'manual', signal: AbortSignal.timeout(20000),
    });
    const outputHeaders = {};
    for (const name of ['content-type', 'cache-control', 'location', 'www-authenticate',
      'mcp-protocol-version', 'x-frame-options']) {
      const value = upstream.headers.get(name);
      if (value) outputHeaders[name] = value.replaceAll(backend.origin, `https://${publicHost}`);
    }
    const cookies = upstream.headers.getSetCookie?.() || [];
    if (cookies.length) outputHeaders['set-cookie'] = cookies;
    response.writeHead(upstream.status, outputHeaders).end(Buffer.from(await upstream.arrayBuffer()));
  } catch {
    response.writeHead(502).end();
  }
});

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`OAuth MCP gateway listening on 127.0.0.1:${port}\n`);
});
