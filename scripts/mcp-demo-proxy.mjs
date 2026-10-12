import { timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';

const backendUrl = process.env.MCP_BACKEND_URL || 'http://127.0.0.1:8080';
const accessKey = process.env.MCP_DEMO_ACCESS_KEY || '';
const port = Number(process.env.MCP_PROXY_PORT || 8081);

if (!accessKey) throw new Error('MCP_DEMO_ACCESS_KEY is required');

function validKey(value) {
  if (!value) return false;
  const provided = Buffer.from(value);
  const expected = Buffer.from(accessKey);
  return provided.length === expected.length && timingSafeEqual(provided, expected);
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url || '/', 'http://localhost');
  if (url.pathname !== '/mcp' || request.method !== 'POST') {
    response.writeHead(404).end();
    return;
  }
  if (!validKey(url.searchParams.get('mcp_demo_key'))) {
    response.writeHead(403).end();
    return;
  }

  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 1024 * 1024) {
      response.writeHead(413).end();
      return;
    }
    chunks.push(chunk);
  }

  try {
    const headers = new Headers({
      'Content-Type': request.headers['content-type'] || 'application/json',
      Accept: request.headers.accept || 'application/json, text/event-stream',
      Host: '127.0.0.1:8080',
    });
    for (const name of ['origin', 'mcp-protocol-version', 'authorization']) {
      const value = request.headers[name];
      if (value) headers.set(name, value);
    }
    const upstream = await fetch(`${backendUrl}/mcp?mcp_demo_key=${encodeURIComponent(accessKey)}`, {
      method: 'POST', headers, body: Buffer.concat(chunks),
    });
    const upstreamBody = Buffer.from(await upstream.arrayBuffer());
    response.writeHead(upstream.status, {
      'Content-Type': upstream.headers.get('content-type') || 'application/json',
      ...(upstream.headers.get('mcp-protocol-version') ? { 'MCP-Protocol-Version': upstream.headers.get('mcp-protocol-version') } : {}),
    });
    response.end(upstreamBody);
  } catch {
    response.writeHead(502).end();
  }
});

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`MCP demo proxy listening on 127.0.0.1:${port}; only /mcp is forwarded.\n`);
});
