import { mockTransactions } from '../data/mock-transactions.js';
import { normalizeTransaction } from './normalizer.js';

export function getMockTransactions() {
  // Reject the response as a whole if the provider fixture itself becomes invalid.
  return mockTransactions.map(normalizeTransaction);
}
