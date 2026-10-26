import { TransactionResponse } from './transaction.model';
import { BudgetResponse } from './budget.model';
export type { TransactionResponse } from './transaction.model';
export type { BudgetResponse, BudgetStatus } from './budget.model';
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
export interface DashboardResponse extends ReportingPeriod {
  summary: FinancialSummary;
  expensesByCategory: CategoryBreakdown[];
  incomeByCategory: CategoryBreakdown[];
  dailySpendingTrend: DailyTrend[];
  recentTransactions: TransactionResponse[];
  budgetUtilization: BudgetResponse[];
  topSpendingCategories: CategoryBreakdown[];
}
