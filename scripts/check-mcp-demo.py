#!/usr/bin/env python3
"""Validate the wire protocol and demo identity without printing the access URL."""
import json
import os
import sys
import urllib.request
import urllib.error


def call(method, params, request_id):
    request = urllib.request.Request(
        os.environ["MCP_CHECK_URL"],
        data=json.dumps({"jsonrpc": "2.0", "id": request_id, "method": method, "params": params}).encode(),
        headers={"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
                 "MCP-Protocol-Version": "2025-06-18"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        raw = response.read().decode()
    if raw.startswith("event:") or raw.startswith("data:"):
        raw = next(line[5:].strip() for line in raw.splitlines() if line.startswith("data:"))
    message = json.loads(raw)
    if message.get("id") != request_id or "error" in message or "result" not in message:
        raise ValueError("Invalid MCP JSON-RPC response")
    result = message["result"]
    if result.get("isError"):
        raise ValueError("MCP demo identity/tool failed: " + str(result.get("content")))
    return result


def main():
    if os.environ.get("MCP_CHECK_DISCOVERY") == "true":
        request = urllib.request.Request(
            os.environ["MCP_CHECK_URL"],
            data=json.dumps({"jsonrpc": "2.0", "id": 0, "method": "server/discover", "params": {
                "_meta": {"io.modelcontextprotocol/protocolVersion": "2026-07-28"}}}).encode(),
            headers={"Content-Type": "application/json", "Accept": "application/json, text/event-stream"},
        )
        try:
            urllib.request.urlopen(request, timeout=20)
            raise ValueError("Legacy SDK must not advertise unsupported modern discovery")
        except urllib.error.HTTPError as response:
            message = json.loads(response.read())
            if response.code != 404 or message.get("error", {}).get("code") != -32601 or message.get("id") != 0:
                raise ValueError("Discovery must return HTTP 404 and JSON-RPC -32601 for legacy fallback")
    call("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                        "clientInfo": {"name": "demo-check", "version": "1"}}, 1)
    tools = call("tools/list", {}, 2)["tools"]
    expected = {"tenancies_list", "work_items_list", "work_items_create", "work_items_update", "work_items_delete"}
    if {tool["name"] for tool in tools} != expected:
        raise ValueError("The expected demo tools were not registered")
    for tool in tools:
        for schema in ("inputSchema", "outputSchema"):
            if schema in tool and tool[schema].get("type") != "object":
                raise ValueError(tool["name"] + ": " + schema + " must be an object for 2025 clients")
    result = call("tools/call", {"name": "tenancies_list", "arguments": {}}, 3)
    data = result.get("structuredContent")
    if data is None:
        data = json.loads(next(block["text"] for block in result["content"] if block["type"] == "text"))
    if not isinstance(data, dict) or not isinstance(data.get("tenancies"), list) or not data["tenancies"]:
        raise ValueError("The demo identity has no memberships")
    print("MCP verificato: 5 tool compatibili e identità demo con membership valida.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("MCP check failed: " + str(error), file=sys.stderr)
        sys.exit(1)
