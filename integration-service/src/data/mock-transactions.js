// Fictional PHP records. Fixed IDs and dates make repeat imports deterministic.
export const mockTransactions = Object.freeze([
  { externalTransactionId: 'nodejsbank-2026-10-salary', amount: '35000.00', currency: 'PHP', direction: 'credit', description: 'Monthly salary', transactionDate: '2026-10-01', categoryHint: 'SALARY' },
  { externalTransactionId: 'nodejsbank-2026-10-freelance', amount: '2500.50', currency: 'PHP', direction: 'credit', description: 'Freelance project', transactionDate: '2026-10-05', categoryHint: 'FREELANCE' },
  { externalTransactionId: 'nodejsbank-2026-10-food', amount: '425.75', currency: 'PHP', direction: 'debit', description: 'Grocery purchase', transactionDate: '2026-10-06', categoryHint: 'FOOD' },
  { externalTransactionId: 'nodejsbank-2026-10-transport', amount: '120.00', currency: 'PHP', direction: 'debit', description: 'Transport fare', transactionDate: '2026-10-07', categoryHint: 'TRANSPORTATION' },
  { externalTransactionId: 'nodejsbank-2026-10-housing', amount: '8000.00', currency: 'PHP', direction: 'debit', description: 'Housing expense', transactionDate: '2026-10-08', categoryHint: 'HOUSING' },
].map(record => Object.freeze(record)));
