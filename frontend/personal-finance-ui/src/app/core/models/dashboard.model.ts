import { CategoryType } from './category.model';
export interface ReportingPeriod {
  month: number;
  year: number;
}
// Java BigDecimal fields are JSON numbers, LocalDate fields are YYYY-MM-DD,
// and Instant fields are ISO timestamps. No currency or account balance is returned.
export interface FinancialSummary {
  totalIncome: number;
  totalExpenses: number;
  netSavings: number;
  savingsRate: number | null;
}
export interface CategoryBreakdown {
  categoryId: number;
  categoryName: string;
  color: string | null;
  amount: number;
}
export interface DailyTrend {
  date: string;
  amount: number;
}
export interface TransactionResponse {
  id: number;
  categoryId: number;
  type: CategoryType;
  amount: number;
  description: string | null;
  transactionDate: string;
  source: 'MANUAL' | 'MOCK_PROVIDER';
  createdAt: string;
  updatedAt: string;
}
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
export interface DashboardResponse extends ReportingPeriod {
  summary: FinancialSummary;
  expensesByCategory: CategoryBreakdown[];
  incomeByCategory: CategoryBreakdown[];
  dailySpendingTrend: DailyTrend[];
  recentTransactions: TransactionResponse[];
  budgetUtilization: BudgetResponse[];
  topSpendingCategories: CategoryBreakdown[];
}
