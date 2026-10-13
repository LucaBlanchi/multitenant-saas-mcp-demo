#!/usr/bin/env python3
"""Exercise authorization-code + PKCE for two local users without printing tokens."""
import base64
import hashlib
import http.cookiejar
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("MCP_OAUTH_TEST_BASE", "http://127.0.0.1:8086")
CLIENT_ID = os.environ.get("MCP_OAUTH_CLIENT_ID", "local-mcp-client")
REDIRECT = os.environ.get("MCP_OAUTH_REDIRECT_URI", "http://127.0.0.1:9876/callback")
RESOURCE = os.environ.get("MCP_OAUTH_TEST_RESOURCE", BASE + "/mcp")
MCP_URL = os.environ.get("MCP_OAUTH_TEST_MCP_URL", BASE + "/mcp")


class CallbackReached(Exception):
    def __init__(self, url):
        self.url = url


class CaptureCallback(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        if newurl.startswith(REDIRECT + "?"):
            raise CallbackReached(newurl)
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def post_form(opener, url, fields):
    request = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(),
                                     headers={"Content-Type": "application/x-www-form-urlencoded"})
    return opener.open(request)


def form_fields(page):
    return dict(re.findall(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', page))


def token_for(email, password):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
                                        CaptureCallback())
    verifier = secrets.token_urlsafe(40)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    state = secrets.token_urlsafe(16)
    params = {"response_type": "code", "client_id": CLIENT_ID, "redirect_uri": REDIRECT,
              "scope": "mcp:connect", "state": state, "code_challenge": challenge,
              "code_challenge_method": "S256", "resource": RESOURCE}
    page = opener.open(BASE + "/oauth2/authorize?" + urllib.parse.urlencode(params)).read().decode()
    login_fields = form_fields(page)
    if "_csrf" not in login_fields:
        raise AssertionError("OAuth did not present a CSRF-protected login form")
    try:
        response = post_form(opener, BASE + "/login", {"username": email, "password": password,
                                                        "_csrf": login_fields["_csrf"]})
        page = response.read().decode()
        if "Consent" in page or "consent" in page:
            fields = form_fields(page)
            fields["scope"] = "mcp:connect"
            try:
                post_form(opener, BASE + "/oauth2/authorize", fields)
            except CallbackReached as callback:
                callback_url = callback.url
            else:
                raise AssertionError("OAuth consent did not redirect to the client")
        else:
            raise AssertionError("Expected an OAuth consent page; received " + response.geturl())
    except CallbackReached as callback:
        callback_url = callback.url
    returned = dict(urllib.parse.parse_qsl(urllib.parse.urlparse(callback_url).query))
    if returned.get("state") != state or not returned.get("code"):
        raise AssertionError("OAuth callback did not contain the expected state and code")
    token_response = post_form(opener, BASE + "/oauth2/token", {
        "grant_type": "authorization_code", "client_id": CLIENT_ID, "code": returned["code"],
        "redirect_uri": REDIRECT, "code_verifier": verifier, "resource": RESOURCE,
    })
    token = json.load(token_response)
    if token.get("token_type", "").lower() != "bearer" or not token.get("access_token"):
        raise AssertionError("OAuth token endpoint did not issue a bearer token")
    return token["access_token"]


def call_tool(token, name, arguments, request_id):
    data = json.dumps({"jsonrpc": "2.0", "id": request_id, "method": "tools/call",
                       "params": {"name": name, "arguments": arguments}}).encode()
    request = urllib.request.Request(MCP_URL, data=data, headers={
        "Authorization": "Bearer " + token, "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream", "MCP-Protocol-Version": "2025-06-18"})
    raw = urllib.request.urlopen(request).read().decode()
    if raw.startswith("event:") or raw.startswith("data:"):
        raw = next(line[5:].strip() for line in raw.splitlines() if line.startswith("data:"))
    result = json.loads(raw)["result"]
    if result.get("isError"):
        return None, True
    if result.get("structuredContent") is not None:
        return result["structuredContent"], False
    return json.loads(next(block["text"] for block in result["content"] if block["type"] == "text")), False


def main():
    first_password = os.environ.get("MCP_OAUTH_TEST_PASSWORD_USER1") or os.environ["MCP_OAUTH_TEST_PASSWORD"]
    second_password = os.environ.get("MCP_OAUTH_TEST_PASSWORD_USER2") or os.environ["MCP_OAUTH_TEST_PASSWORD"]
    first = token_for("user1@email.com", first_password)
    second = token_for("user2@email.com", second_password)
    first_account, first_account_error = call_tool(first, "account_me", {}, 1)
    second_account, second_account_error = call_tool(second, "account_me", {}, 2)
    if first_account_error or second_account_error or first_account["id"] == second_account["id"]:
        raise AssertionError("Two OAuth logins resolved to the same account")
    if first_account["email"] != "user1@email.com" or second_account["email"] != "user2@email.com":
        raise AssertionError("OAuth token resolved to the wrong account")
    first_tenants, first_error = call_tool(first, "tenancies_list", {}, 3)
    second_tenants, second_error = call_tool(second, "tenancies_list", {}, 4)
    if first_error or second_error:
        raise AssertionError("Authenticated tenancy tool failed")
    first_roles = {item["slug"]: item["role"] for item in first_tenants["tenancies"]}
    second_roles = {item["slug"]: item["role"] for item in second_tenants["tenancies"]}
    if first_roles != {"company1": "ADMIN", "company2": "MEMBER"}:
        raise AssertionError("Unexpected memberships for user1: " + repr(first_roles))
    if second_roles != {"company1": "MEMBER"}:
        raise AssertionError("Unexpected memberships for user2: " + repr(second_roles))
    company1_id = next(item["id"] for item in first_tenants["tenancies"] if item["slug"] == "company1")
    company2_id = next(item["id"] for item in first_tenants["tenancies"] if item["slug"] == "company2")
    _, forbidden_tenant = call_tool(second, "work_items_list", {"tenancyId": company2_id}, 5)
    _, forbidden_write = call_tool(second, "work_items_create", {
        "tenancyId": company1_id, "title": "oauth-permission-check"}, 6)
    if not forbidden_tenant or not forbidden_write:
        raise AssertionError("MEMBER token accessed a forbidden tenancy or created a work item")
    print("OAuth verificato: due login PKCE distinti, identità personali e permessi tenant/ruolo isolati.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("OAuth check failed: " + str(error), file=sys.stderr)
        sys.exit(1)
