import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { normalizeTransaction, InvalidProviderRecord } from '../src/services/normalizer.js';
import { mockTransactions } from '../src/data/mock-transactions.js';

const key = 'test-only-internal-integration-key-at-least-32-bytes';

async function api(context, options = {}) {
  const server = createApp({ internalApiKey: key, ...options }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  context.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const base = `http://127.0.0.1:${server.address().port}`;
  return (path, headers = {}) => fetch(base + path, { headers });
}

test('health is public and contains no configuration', async context => {
  const request = await api(context);
  const response = await request('/health');
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: 'healthy' });
  assert.equal(response.headers.get('x-powered-by'), null);
});

test('internal endpoint returns deterministic normalized fictional transactions', async context => {
  const request = await api(context);
  const response = await request('/internal/mock-transactions', { 'X-Internal-API-Key': key });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const body = await response.json();
  assert.equal(body.transactions.length, 5);
  assert.deepEqual(body.transactions, mockTransactions.map(normalizeTransaction));
  assert.equal(body.transactions[0].type, 'INCOME');
  assert.equal(body.transactions[2].type, 'EXPENSE');
  assert.equal(body.transactions[1].amount, '2500.50');
  assert.equal(body.transactions[0].source, 'MOCK_PROVIDER');
  const again = await request('/internal/mock-transactions', { 'X-Internal-API-Key': key });
  assert.deepEqual(await again.json(), body);
  assert.ok(!JSON.stringify(body).includes(key));
});

for (const headers of [{}, { 'X-Internal-API-Key': 'invalid' }, { Authorization: 'Bearer fake-user-jwt', 'X-User-Id': 'owner' }]) {
  test(`missing or invalid internal authentication ${JSON.stringify(Object.keys(headers))}`, async context => {
    const request = await api(context);
    const response = await request('/internal/mock-transactions', headers);
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { code: 'UNAUTHORIZED', message: 'Missing or invalid internal API key' });
  });
}

test('owner and unsupported query parameters are rejected', async context => {
  const request = await api(context);
  assert.equal((await request('/internal/mock-transactions?userId=other', { 'X-Internal-API-Key': key })).status, 400);
});

test('credit/debit mapping and exact decimal padding preserve large amounts', () => {
  for (const [amount, expected] of [['1', '1.00'], ['1.2', '1.20'], ['0.01', '0.01'], ['99999999999999999.99', '99999999999999999.99']]) {
    const normalized = normalizeTransaction({ ...mockTransactions[0], amount });
    assert.equal(normalized.amount, expected);
    assert.equal(normalized.type, 'INCOME');
  }
  assert.equal(normalizeTransaction({ ...mockTransactions[0], direction: 'debit' }).type, 'EXPENSE');
  assert.equal(normalizeTransaction({ ...mockTransactions[0], transactionDate: '2024-02-29' }).transactionDate, '2024-02-29');
});

const invalidRecords = [null, [], {}, { ...mockTransactions[0], ownerId: 'injected' },
  ...[0, -1, 1.5, '', '0', '0.00', '-1.00', '1.001', '1e3', 'NaN', '01.00', '100000000000000000.00'].map(amount => ({ ...mockTransactions[0], amount })),
  ...['2026-02-29', '2026-02-30', '2026-13-01', '2026-04-31', '2026-1-01', '0000-01-01', 'yesterday'].map(transactionDate => ({ ...mockTransactions[0], transactionDate })),
  { ...mockTransactions[0], currency: 'USD' }, { ...mockTransactions[0], direction: 'refund' },
  { ...mockTransactions[0], externalTransactionId: '' }, { ...mockTransactions[0], description: 'x'.repeat(256) },
  { ...mockTransactions[0], description: ' ' }, { ...mockTransactions[0], categoryHint: '' },
];
for (const [index, record] of invalidRecords.entries()) {
  test(`reject malformed provider record ${index + 1}`, () => assert.throws(() => normalizeTransaction(record), InvalidProviderRecord));
}

test('provider errors never expose exception text or stack traces', async context => {
  const request = await api(context, { provider: () => { throw new Error('private-provider-marker'); } });
  const response = await request('/internal/mock-transactions', { 'X-Internal-API-Key': key });
  assert.equal(response.status, 500);
  assert.deepEqual(await response.json(), { code: 'INTERNAL_ERROR', message: 'Unexpected service error' });
});

test('invalid provider data has a controlled response', async context => {
  const request = await api(context, { provider: () => [normalizeTransaction({})] });
  const response = await request('/internal/mock-transactions', { 'X-Internal-API-Key': key });
  assert.equal(response.status, 502);
  assert.equal((await response.json()).code, 'INVALID_PROVIDER_RECORD');
});

test('configuration validates secrets and port without echoing values', () => {
  assert.throws(() => loadConfig({ INTEGRATION_INTERNAL_API_KEY: '' }), /at least 32/);
  assert.throws(() => loadConfig({ INTEGRATION_INTERNAL_API_KEY: key, PORT: '0' }), /PORT/);
  assert.throws(() => loadConfig({ INTEGRATION_INTERNAL_API_KEY: key, PORT: '1.5' }), /PORT/);
  assert.throws(() => createApp({ internalApiKey: '' }), /at least 32/);
  assert.deepEqual(loadConfig({ INTEGRATION_INTERNAL_API_KEY: key }), { internalApiKey: key, port: 3001 });
});
