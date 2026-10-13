import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Alert, AppShell, Badge, Button, Checkbox, Code, Group, MantineProvider,
  Paper, PasswordInput, Select, Stack, Table, Tabs, Text, TextInput, Title,
} from '@mantine/core';
import { IconLogout } from '@tabler/icons-react';
import '@mantine/core/styles.css';
import './index.css';

const API = import.meta.env.VITE_API_URL || 'http://127.0.0.1:8080';

function App() {
  const [token, setToken] = useState(localStorage.getItem('root-token') || '');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');
  const [tenancies, setTenancies] = useState([]);
  const [users, setUsers] = useState([]);
  const [memberships, setMemberships] = useState([]);
  const [busy, setBusy] = useState(false);

  const session = useRef(token);
  session.current = token;

  function assertCurrentRequest() {
    if (session.current !== token) throw new DOMException('Session changed', 'AbortError');
  }
  function showError(err) { if (err.name !== 'AbortError') setError(err.message); }

  async function api(path, options = {}) {
    assertCurrentRequest();
    try {
      const response = await fetch(API + path, {
        ...options,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...options.headers },
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || response.statusText);
      return result;
    } finally { assertCurrentRequest(); }
  }

  async function load() {
    try {
      const [currentUser, availableTenancies, allUsers, allMemberships] = await Promise.all([
        api('/api/auth/me'), api('/api/superadmin/tenancies'), api('/api/superadmin/users'), api('/api/superadmin/memberships'),
      ]);
      if (!currentUser.superadmin) throw Error('Account superadmin richiesto');
      setUser(currentUser);
      setTenancies(availableTenancies);
      setUsers(allUsers);
      setMemberships(allMemberships);
      setError('');
    } catch (err) {
      showError(err);
    }
  }

  useEffect(() => { if (token) load(); else setUser(null); }, [token]);

  async function login(event) {
    event.preventDefault();
    return loginWithCredentials(event.currentTarget.email.value, event.currentTarget.password.value);
  }

  async function loginWithCredentials(email, password) {
    setBusy(true);
    try {
      const response = await fetch(API + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const result = await response.json();
      if (!response.ok) throw Error(result.message);
      session.current = result.token;
      setUser(null); setTenancies([]); setUsers([]); setMemberships([]);
      localStorage.setItem('root-token', result.token);
      setToken(result.token);
    } catch (err) {
      showError(err);
    } finally {
      setBusy(false);
    }
  }

  async function submit(event, path, body, reset) {
    event.preventDefault();
    try {
      await api(path, { method: 'POST', body: JSON.stringify(body) });
      reset?.();
      await load();
    } catch (err) {
      showError(err);
    }
  }

  function logout() {
    session.current = '';
    setUser(null); setTenancies([]); setUsers([]); setMemberships([]); setError('');
    localStorage.removeItem('root-token');
    setToken('');
  }

  if (!user) return (
    <main className="login">
      <Paper className="login-card" p="xl" radius="lg">
        <Title order={3} mb="md">Accedi</Title>
        {error && <Alert color="red" mb="md">{error}</Alert>}
        <form onSubmit={login}>
          <Stack>
            <TextInput name="email" label="Email" type="email" required />
            <PasswordInput name="password" label="Password" required />
            <Button color="violet" type="submit" loading={busy}>Accedi</Button>
            {import.meta.env.DEV && <Button variant="light" color="gray" type="button" loading={busy} onClick={() => loginWithCredentials('root@example.test', 'ChangeMe123!')}>Entra in demo</Button>}
          </Stack>
        </form>
      </Paper>
    </main>
  );

  return (
    <AppShell header={{ height: 60 }} padding="lg">
      <AppShell.Header>
        <Group h="100%" px="lg" justify="space-between">
          <Text fw={700}>Superadmin</Text>
          <Group gap="sm">
            <Text size="sm">{user.displayName}</Text>
            <Button size="compact-sm" variant="subtle" leftSection={<IconLogout size={15} />} onClick={logout}>Esci</Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <div className="wrap">
          {error && <Alert color="red" mb="md" withCloseButton onClose={() => setError('')}>{error}</Alert>}
          <Tabs defaultValue="tenancies" color="violet">
            <Tabs.List mb="md">
              <Tabs.Tab value="tenancies">Tenancy</Tabs.Tab>
              <Tabs.Tab value="users">Utenti</Tabs.Tab>
              <Tabs.Tab value="memberships">Membership</Tabs.Tab>
            </Tabs.List>

            <Tabs.Panel value="tenancies">
              <Paper className="panel" p="md" radius="md">
                <Title order={4} mb="md">Nuova tenancy</Title>
                <TenancyForm onSubmit={submit} />
                <Table mt="md">
                  <Table.Thead><Table.Tr><Table.Th>Nome</Table.Th><Table.Th>Slug</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>{tenancies.map(entry => (
                    <Table.Tr key={entry.id}><Table.Td>{entry.name}</Table.Td><Table.Td><Code>{entry.slug}</Code></Table.Td></Table.Tr>
                  ))}</Table.Tbody>
                </Table>
              </Paper>
            </Tabs.Panel>

            <Tabs.Panel value="users">
              <Paper className="panel" p="md" radius="md">
                <Title order={4} mb="md">Nuovo utente</Title>
                <UserForm onSubmit={submit} />
                <Table mt="md">
                  <Table.Thead><Table.Tr><Table.Th>Nome</Table.Th><Table.Th>Email</Table.Th><Table.Th>Tipo</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>{users.map(entry => (
                    <Table.Tr key={entry.id}>
                      <Table.Td>{entry.displayName}</Table.Td>
                      <Table.Td>{entry.email}</Table.Td>
                      <Table.Td>{entry.superadmin && <Badge color="violet">Superadmin</Badge>}</Table.Td>
                    </Table.Tr>
                  ))}</Table.Tbody>
                </Table>
              </Paper>
            </Tabs.Panel>

            <Tabs.Panel value="memberships">
              <Paper className="panel" p="md" radius="md">
                <Title order={4} mb="md">Associa utente</Title>
                <MembershipForm users={users} tenancies={tenancies} onSubmit={submit} />
                <Table mt="md">
                  <Table.Thead><Table.Tr><Table.Th>Company</Table.Th><Table.Th>Utente</Table.Th><Table.Th>Email</Table.Th><Table.Th>Ruolo</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>{memberships.map(entry => (
                    <Table.Tr key={entry.id}>
                      <Table.Td>{entry.tenancyName}</Table.Td>
                      <Table.Td>{entry.displayName}</Table.Td>
                      <Table.Td>{entry.userEmail}</Table.Td>
                      <Table.Td><Badge color={entry.role === 'ADMIN' ? 'violet' : 'gray'}>{entry.role}</Badge></Table.Td>
                    </Table.Tr>
                  ))}</Table.Tbody>
                </Table>
              </Paper>
            </Tabs.Panel>
          </Tabs>
        </div>
      </AppShell.Main>
    </AppShell>
  );
}

function TenancyForm({ onSubmit }) {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  return (
    <form className="form" onSubmit={event => onSubmit(event, '/api/superadmin/tenancies', { name, slug }, () => { setName(''); setSlug(''); })}>
      <TextInput label="Nome" value={name} onChange={event => setName(event.currentTarget.value)} required />
      <TextInput label="Slug" value={slug} onChange={event => setSlug(event.currentTarget.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} required />
      <Button color="violet" type="submit">Crea</Button>
    </form>
  );
}

function UserForm({ onSubmit }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [superadmin, setSuperadmin] = useState(false);
  return (
    <form className="form" onSubmit={event => onSubmit(event, '/api/superadmin/users', { displayName: name, email, password, superadmin }, () => { setName(''); setEmail(''); setPassword(''); setSuperadmin(false); })}>
      <TextInput label="Nome" value={name} onChange={event => setName(event.currentTarget.value)} required />
      <TextInput label="Email" type="email" value={email} onChange={event => setEmail(event.currentTarget.value)} required />
      <PasswordInput label="Password iniziale" value={password} onChange={event => setPassword(event.currentTarget.value)} minLength={12} required />
      <Checkbox label="Superadmin" checked={superadmin} onChange={event => setSuperadmin(event.currentTarget.checked)} />
      <Button color="violet" type="submit">Crea</Button>
    </form>
  );
}

function MembershipForm({ users, tenancies, onSubmit }) {
  const [userId, setUserId] = useState(null);
  const [tenancyId, setTenancyId] = useState(null);
  const [role, setRole] = useState('MEMBER');
  return (
    <form className="form" onSubmit={event => onSubmit(event, '/api/superadmin/memberships', { userId, tenancyId, role })}>
      <Select label="Utente" searchable data={users.filter(entry => !entry.superadmin).map(entry => ({ value: entry.id, label: `${entry.displayName} · ${entry.email}` }))} value={userId} onChange={setUserId} required />
      <Select label="Tenancy" searchable data={tenancies.map(entry => ({ value: entry.id, label: entry.name }))} value={tenancyId} onChange={setTenancyId} required />
      <Select label="Ruolo" data={['ADMIN', 'MEMBER']} value={role} onChange={setRole} required />
      <Button color="violet" type="submit">Associa</Button>
    </form>
  );
}

createRoot(document.getElementById('root')).render(
  <MantineProvider theme={{ primaryColor: 'violet', fontFamily: 'Inter, sans-serif' }}><App /></MantineProvider>,
);
