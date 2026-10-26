export type BudgetStatus = 'SAFE' | 'WARNING' | 'EXCEEDED';
export interface BudgetResponse {
  id: number;
  categoryId: number;
  month: number;
  year: number;
  monthlyLimit: number;
  alertThreshold: number;
  spentAmount: number;
  remainingAmount: number;
  percentageUsed: number;
  budgetStatus: BudgetStatus;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetRequest {
  categoryId: number;
  month: number;
  year: number;
  monthlyLimit: number;
  alertThreshold: number;
}
