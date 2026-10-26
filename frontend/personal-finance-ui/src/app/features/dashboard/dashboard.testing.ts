import { DashboardResponse, ReportingPeriod } from '../../core/models/dashboard.model';
export function emptyDashboard(period: ReportingPeriod): DashboardResponse {
  return {
    ...period,
    summary: { totalIncome: 0, totalExpenses: 0, netSavings: 0, savingsRate: null },
    expensesByCategory: [],
    incomeByCategory: [],
    dailySpendingTrend: [],
    recentTransactions: [],
    budgetUtilization: [],
    topSpendingCategories: [],
  };
}
export function populatedDashboard(period: ReportingPeriod): DashboardResponse {
  const expenses = [{ categoryId: 1, categoryName: 'Food', color: '#14b8a6', amount: 1200.35 }];
  return {
    ...emptyDashboard(period),
    summary: {
      totalIncome: 1000.1,
      totalExpenses: 1200.35,
      netSavings: -200.25,
      savingsRate: -20.02,
    },
    expensesByCategory: expenses,
    incomeByCategory: [{ categoryId: 2, categoryName: 'Salary', color: null, amount: 1000.1 }],
    topSpendingCategories: expenses,
    dailySpendingTrend: [
      { date: `${period.year}-${String(period.month).padStart(2, '0')}-01`, amount: 1200.35 },
    ],
    recentTransactions: [
      {
        id: 1,
        categoryId: 1,
        type: 'EXPENSE',
        amount: 1200.35,
        description: 'Groceries',
        transactionDate: `${period.year}-${String(period.month).padStart(2, '0')}-01`,
        source: 'MANUAL',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ],
    budgetUtilization: [
      {
        id: 1,
        categoryId: 1,
        ...period,
        monthlyLimit: 1000,
        alertThreshold: 80,
        spentAmount: 1200.35,
        remainingAmount: -200.35,
        percentageUsed: 120.04,
        budgetStatus: 'EXCEEDED',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ],
  };
}
