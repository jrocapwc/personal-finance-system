export class InvalidProviderRecord extends Error {
  constructor() { super('Mock provider returned an invalid transaction'); }
}

const fields = ['externalTransactionId', 'amount', 'currency', 'direction', 'description', 'transactionDate', 'categoryHint'];

export function normalizeTransaction(record) {
  const invalid = () => { throw new InvalidProviderRecord(); };
  if (!record || typeof record !== 'object' || Array.isArray(record) ||
      Object.keys(record).length !== fields.length || fields.some(key => typeof record[key] !== 'string')) invalid();
  const { externalTransactionId, amount, currency, direction, description, transactionDate, categoryHint } = record;
  if (!/^[A-Za-z0-9_-]{1,100}$/.test(externalTransactionId) || currency !== 'PHP' ||
      !['credit', 'debit'].includes(direction) || description.trim().length < 1 || description.length > 255 ||
      !/^[A-Z][A-Z_]{0,99}$/.test(categoryHint)) invalid();
  // String operations and BigInt only: no financial value is parsed as a Number.
  if (!/^(0|[1-9]\d{0,16})(\.\d{1,2})?$/.test(amount)) invalid();
  const [whole, fraction = ''] = amount.split('.');
  const decimal = `${whole}.${fraction.padEnd(2, '0')}`;
  if (BigInt(whole + fraction.padEnd(2, '0')) <= 0n) invalid();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(transactionDate) || transactionDate.slice(0, 4) === '0000') invalid();
  const date = new Date(`${transactionDate}T00:00:00.000Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== transactionDate) invalid();
  return { externalTransactionId, type: direction === 'credit' ? 'INCOME' : 'EXPENSE', amount: decimal,
    description: description.trim(), transactionDate, categoryHint, source: 'MOCK_PROVIDER' };
}
