import { CategoryResponse } from '../../core/models/category.model';
import { TransactionPageResponse, TransactionResponse } from '../../core/models/transaction.model';
import { BudgetResponse } from '../../core/models/budget.model';
export const financeCategories: CategoryResponse[] = [
  {
    id: 1,
    name: 'Food',
    type: 'EXPENSE',
    color: '#123456',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
  {
    id: 2,
    name: 'Salary',
    type: 'INCOME',
    color: null,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
];
export const financeTransaction: TransactionResponse = {
  id: 3,
  categoryId: 1,
  type: 'EXPENSE',
  amount: 125.5,
  description: 'Groceries',
  transactionDate: '2026-10-26',
  source: 'MANUAL',
  createdAt: '2026-10-26T00:00:00Z',
  updatedAt: '2026-10-26T00:00:00Z',
};
export const financeBudget: BudgetResponse = {
  id: 4,
  categoryId: 1,
  month: 10,
  year: 2026,
  monthlyLimit: 100,
  alertThreshold: 80,
  spentAmount: 125.5,
  remainingAmount: -25.5,
  percentageUsed: 125.5,
  budgetStatus: 'EXCEEDED',
  createdAt: '2026-10-26T00:00:00Z',
  updatedAt: '2026-10-26T00:00:00Z',
};
export function financePage(page = 0, size = 20): TransactionPageResponse {
  return {
    content: [financeTransaction],
    page,
    size,
    totalElements: 1,
    totalPages: 1,
    first: page === 0,
    last: true,
    sort: 'transactionDate',
    direction: 'DESC',
  };
}
export function installDialogTesting(): void {
  // jsdom has no native modal/focus implementation. Real-browser checks cover those behaviors.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    },
  });
}
