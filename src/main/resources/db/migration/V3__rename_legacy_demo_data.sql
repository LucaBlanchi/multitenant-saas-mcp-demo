-- V2 recognized the short tenant names, but older demo databases used these longer names.
-- Match only the original sample names and memberships; preserve all IDs and business data.
UPDATE tenancies
SET name = 'Company 1', slug = 'company1'
WHERE slug = 'acme' AND name = 'Acme Corporation'
  AND EXISTS (
      SELECT 1 FROM memberships m JOIN users u ON u.id = m.user_id
      WHERE m.tenancy_id = tenancies.id AND m.role = 'ADMIN' AND u.email = 'admin@acme.test'
  )
  AND EXISTS (
      SELECT 1 FROM memberships m JOIN users u ON u.id = m.user_id
      WHERE m.tenancy_id = tenancies.id AND m.role = 'MEMBER' AND u.email = 'member@acme.test'
  )
  AND NOT EXISTS (
      SELECT 1 FROM tenancies other WHERE other.id <> tenancies.id
      AND (other.slug = 'company1' OR other.name = 'Company 1')
  );

UPDATE tenancies
SET name = 'Company 2', slug = 'company2'
WHERE slug = 'globex' AND name = 'Globex Industries'
  AND EXISTS (
      SELECT 1 FROM memberships m JOIN users u ON u.id = m.user_id
      WHERE m.tenancy_id = tenancies.id AND m.role = 'MEMBER' AND u.email = 'admin@acme.test'
  )
  AND NOT EXISTS (
      SELECT 1 FROM tenancies other WHERE other.id <> tenancies.id
      AND (other.slug = 'company2' OR other.name = 'Company 2')
  );

UPDATE users
SET email = 'user1@email.com', display_name = 'User 1'
WHERE email = 'admin@acme.test'
  AND EXISTS (
      SELECT 1 FROM memberships m JOIN tenancies t ON t.id = m.tenancy_id
      WHERE m.user_id = users.id AND t.slug = 'company1' AND m.role = 'ADMIN'
  )
  AND NOT EXISTS (SELECT 1 FROM users other WHERE other.id <> users.id AND other.email = 'user1@email.com');

UPDATE users
SET email = 'user2@email.com', display_name = 'User 2'
WHERE email = 'member@acme.test'
  AND EXISTS (
      SELECT 1 FROM memberships m JOIN tenancies t ON t.id = m.tenancy_id
      WHERE m.user_id = users.id AND t.slug = 'company1' AND m.role = 'MEMBER'
  )
  AND NOT EXISTS (SELECT 1 FROM users other WHERE other.id <> users.id AND other.email = 'user2@email.com');
