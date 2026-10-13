SHELL := /bin/bash
DB_NAME ?= tenancy_platform
DB_USER ?= postgres
DB_HOST ?= localhost
DB_PORT ?= 5432
MCP_OAUTH_BACKEND_PORT ?= 8086

.PHONY: setup db-start db-create dev backend backend-oauth-local mcp-chatgpt-oauth tenant-ui admin-ui test build clean
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

backend-oauth-local:
	SERVER_ADDRESS=127.0.0.1 SERVER_PORT=$(MCP_OAUTH_BACKEND_PORT) MCP_ALLOWED_HOSTS=127.0.0.1:$(MCP_OAUTH_BACKEND_PORT) SPRING_PROFILES_ACTIVE=oauth-local APP_SEED_ENABLED=false DB_URL=jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$(DB_NAME) DB_USER=$(DB_USER) mvn spring-boot:run

mcp-chatgpt-oauth:
	./scripts/mcp-chatgpt-oauth.sh

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
