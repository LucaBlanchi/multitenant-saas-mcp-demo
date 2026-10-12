CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    superadmin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users (email);

CREATE TABLE IF NOT EXISTS tenancies (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tenancies_name ON tenancies (name);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tenancies_slug ON tenancies (slug);

CREATE TABLE IF NOT EXISTS memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    tenancy_id UUID NOT NULL REFERENCES tenancies (id),
    role VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_memberships_user_tenancy ON memberships (user_id, tenancy_id);
CREATE INDEX IF NOT EXISTS idx_memberships_tenancy_user ON memberships (tenancy_id, user_id);

CREATE TABLE IF NOT EXISTS work_items (
    id UUID PRIMARY KEY,
    tenancy_id UUID NOT NULL REFERENCES tenancies (id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_work_item_tenancy ON work_items (tenancy_id);
