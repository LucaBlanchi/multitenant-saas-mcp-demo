import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ActionIcon, Alert, AppShell, Badge, Button, Group, MantineProvider,
  Paper, PasswordInput, Select, Stack, Table, Text, TextInput, Title,
} from '@mantine/core';
import { IconLogout, IconPlus, IconRefresh, IconTrash } from '@tabler/icons-react';
import '@mantine/core/styles.css';
import './index.css';

const API = import.meta.env.VITE_API_URL || 'http://127.0.0.1:8080';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [user, setUser] = useState(null);
  const [tenancies, setTenancies] = useState([]);
  const [tenant, setTenant] = useState(localStorage.getItem('tenant') || '');
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  async function api(path, opts = {}) {
    const res = await fetch(API + path, {
      ...opts,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(tenant ? { 'X-Tenant-Id': tenant } : {}),
        ...opts.headers,
      },
    });
    if (!res.ok) {
      const result = await res.json().catch(() => ({ message: res.statusText }));
      throw Error(result.message || 'Richiesta non riuscita');
    }
    return res.status === 204 ? null : res.json();
  }

  async function load() {
    try {
      setError('');
      const [currentUser, availableTenancies] = await Promise.all([
        api('/api/auth/me'), api('/api/auth/tenancies'),
      ]);
      setUser(currentUser);
      setTenancies(availableTenancies);
      if (!tenant && availableTenancies.length) {
        setTenant(availableTenancies[0].id);
        localStorage.setItem('tenant', availableTenancies[0].id);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    if (token) load();
    else { setUser(null); setTenancies([]); setItems([]); }
  }, [token]);

  useEffect(() => {
    if (token && tenant) api('/api/work-items').then(setItems).catch(err => setError(err.message));
  }, [token, tenant]);

  async function login(event) {
    event.preventDefault();
    return loginWithCredentials(event.currentTarget.email.value, event.currentTarget.password.value);
  }

  async function loginWithCredentials(email, password) {
    setBusy(true);
    try {
      const res = await fetch(API + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const result = await res.json();
      if (!res.ok) throw Error(result.message);
      localStorage.setItem('token', result.token);
      setToken(result.token);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('tenant');
    setToken('');
    setTenant('');
  }

  async function create(event) {
    event.preventDefault();
    try {
      await api('/api/work-items', { method: 'POST', body: JSON.stringify({ title, description }) });
      setTitle('');
      setDescription('');
      setItems(await api('/api/work-items'));
    } catch (err) { setError(err.message); }
  }

  async function change(item, status) {
    try {
      await api(`/api/work-items/${item.id}`, { method: 'PUT', body: JSON.stringify({ ...item, status }) });
      setItems(await api('/api/work-items'));
    } catch (err) { setError(err.message); }
  }

  async function remove(id) {
    try {
      await api(`/api/work-items/${id}`, { method: 'DELETE' });
      setItems(await api('/api/work-items'));
    } catch (err) { setError(err.message); }
  }

  const selected = tenancies.find(entry => entry.id === tenant);
  const writable = selected?.role === 'ADMIN';

  if (!user) return (
    <main className="login">
      <Paper className="login-card" p="xl" radius="lg">
        <Title order={3} mb="md">Accedi</Title>
        {error && <Alert color="red" mb="md">{error}</Alert>}
        <form onSubmit={login}>
          <Stack>
            <TextInput name="email" label="Email" type="email" required />
            <PasswordInput name="password" label="Password" required />
            <Button type="submit" loading={busy}>Accedi</Button>
            {import.meta.env.DEV && <Button variant="light" color="gray" type="button" loading={busy} onClick={() => loginWithCredentials('user1@email.com', 'ChangeMe123!')}>Entra in demo</Button>}
          </Stack>
        </form>
      </Paper>
    </main>
  );

  return (
    <AppShell header={{ height: 60 }} padding="lg">
      <AppShell.Header>
        <Group h="100%" px="lg" justify="space-between">
          <Text fw={700}>Tenancy</Text>
          <Group gap="sm">
            <Text size="sm">{user.displayName}</Text>
            <Button size="compact-sm" variant="subtle" leftSection={<IconLogout size={15} />} onClick={logout}>Esci</Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <div className="wrap">
          <Group justify="flex-end" mb="md">
            <Select
              w={260}
              aria-label="Organizzazione"
              placeholder="Organizzazione"
              value={tenant || null}
              onChange={value => { setTenant(value || ''); localStorage.setItem('tenant', value || ''); }}
              data={tenancies.map(entry => ({ value: entry.id, label: `${entry.name} · ${entry.role}` }))}
            />
          </Group>
          {error && <Alert color="red" mb="md" withCloseButton onClose={() => setError('')}>{error}</Alert>}
          <Paper className="panel" p="md" radius="md">
            <Group justify="space-between" mb="md">
              <Group gap="sm"><Title order={3}>Work items</Title>{selected && <Badge variant="light">{selected.role}</Badge>}</Group>
              <ActionIcon variant="light" onClick={() => api('/api/work-items').then(setItems).catch(err => setError(err.message))} aria-label="Aggiorna">
                <IconRefresh size={17} />
              </ActionIcon>
            </Group>
            {writable && (
              <form onSubmit={create} className="create-form">
                <TextInput value={title} onChange={event => setTitle(event.currentTarget.value)} placeholder="Titolo" aria-label="Titolo" required />
                <TextInput value={description} onChange={event => setDescription(event.currentTarget.value)} placeholder="Descrizione" aria-label="Descrizione" />
                <Button type="submit" leftSection={<IconPlus size={16} />}>Aggiungi</Button>
              </form>
            )}
            <Table.ScrollContainer minWidth={520}>
              <Table verticalSpacing="sm" highlightOnHover>
                <Table.Thead><Table.Tr><Table.Th>Titolo</Table.Th><Table.Th>Descrizione</Table.Th><Table.Th>Stato</Table.Th>{writable && <Table.Th />}</Table.Tr></Table.Thead>
                <Table.Tbody>
                  {items.map(item => (
                    <Table.Tr key={item.id}>
                      <Table.Td fw={600}>{item.title}</Table.Td>
                      <Table.Td c="dimmed">{item.description}</Table.Td>
                      <Table.Td>{writable
                        ? <Select size="xs" w={145} value={item.status} onChange={status => change(item, status)} data={['OPEN', 'IN_PROGRESS', 'DONE']} />
                        : <Badge variant="light">{item.status}</Badge>}
                      </Table.Td>
                      {writable && <Table.Td><ActionIcon color="red" variant="subtle" onClick={() => remove(item.id)} aria-label="Elimina"><IconTrash size={16} /></ActionIcon></Table.Td>}
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
            {!items.length && <Text ta="center" c="dimmed" py="lg">Nessun elemento</Text>}
          </Paper>
        </div>
      </AppShell.Main>
    </AppShell>
  );
}

createRoot(document.getElementById('root')).render(
  <MantineProvider theme={{ primaryColor: 'teal', fontFamily: 'Inter, sans-serif' }}><App /></MantineProvider>,
);
