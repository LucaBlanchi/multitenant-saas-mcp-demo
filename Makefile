SHELL := /bin/bash
DB_NAME ?= tenancy_platform
DB_USER ?= postgres
DB_HOST ?= localhost
DB_PORT ?= 5432

.PHONY: setup db-start db-create dev backend backend-mcp-demo mcp-demo-proxy tenant-ui admin-ui test build clean
setup:
	brew install maven postgresql@16 node
	brew services start postgresql@16
	@PATH="$$(brew --prefix postgresql@16)/bin:$$PATH" createuser -s $(DB_USER) 2>/dev/null || true
	@PATH="$$(brew --prefix postgresql@16)/bin:$$PATH" createdb -U $(DB_USER) $(DB_NAME) 2>/dev/null || true
	npm --prefix frontend/tenant install
	npm --prefix frontend/superadmin install

db-start:
	brew services start postgresql@16

db-create:
	@PATH="$$(brew --prefix postgresql@16)/bin:$$PATH" createdb -U $(DB_USER) $(DB_NAME) 2>/dev/null || true

dev:
	@echo "Run make backend, make tenant-ui and make admin-ui in separate terminals"

backend:
	DB_URL=jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$(DB_NAME) DB_USER=$(DB_USER) mvn spring-boot:run

backend-mcp-demo:
	@test -n "$(MCP_DEMO_ACCESS_KEY)" || (echo "Set MCP_DEMO_ACCESS_KEY to a random temporary value"; exit 1)
	SERVER_ADDRESS=127.0.0.1 SPRING_PROFILES_ACTIVE=mcp-demo MCP_DEMO_ACCESS_KEY=$(MCP_DEMO_ACCESS_KEY) DB_URL=jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$(DB_NAME) DB_USER=$(DB_USER) mvn spring-boot:run

mcp-demo-proxy:
	@test -n "$(MCP_DEMO_ACCESS_KEY)" || (echo "Set MCP_DEMO_ACCESS_KEY to the same temporary value as the backend"; exit 1)
	MCP_DEMO_ACCESS_KEY=$(MCP_DEMO_ACCESS_KEY) node scripts/mcp-demo-proxy.mjs

tenant-ui:
	npm --prefix frontend/tenant run dev

admin-ui:
	npm --prefix frontend/superadmin run dev

test:
	mvn -q test

build:
	mvn -q package
	npm --prefix frontend/tenant run build
	npm --prefix frontend/superadmin run build

clean:
	mvn clean
	rm -rf frontend/tenant/node_modules frontend/tenant/dist frontend/superadmin/node_modules frontend/superadmin/dist
