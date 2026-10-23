// Fictional PHP records. Fixed IDs and dates make repeat imports deterministic.
export const mockTransactions = Object.freeze([
  { externalTransactionId: 'mock-2026-10-salary', amount: '35000.00', currency: 'PHP', direction: 'credit', description: 'Fictional monthly salary', transactionDate: '2026-10-01', categoryHint: 'SALARY' },
  { externalTransactionId: 'mock-2026-10-freelance', amount: '2500.50', currency: 'PHP', direction: 'credit', description: 'Fictional freelance project', transactionDate: '2026-10-05', categoryHint: 'FREELANCE' },
  { externalTransactionId: 'mock-2026-10-food', amount: '425.75', currency: 'PHP', direction: 'debit', description: 'Fictional grocery purchase', transactionDate: '2026-10-06', categoryHint: 'FOOD' },
  { externalTransactionId: 'mock-2026-10-transport', amount: '120.00', currency: 'PHP', direction: 'debit', description: 'Fictional transport fare', transactionDate: '2026-10-07', categoryHint: 'TRANSPORTATION' },
  { externalTransactionId: 'mock-2026-10-housing', amount: '8000.00', currency: 'PHP', direction: 'debit', description: 'Fictional housing expense', transactionDate: '2026-10-08', categoryHint: 'HOUSING' },
].map(record => Object.freeze(record)));
