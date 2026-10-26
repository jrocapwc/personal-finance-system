import { HttpErrorResponse } from '@angular/common/http';
import {
  CategoryBreakdown,
  DashboardResponse,
  ReportingPeriod,
} from '../../core/models/dashboard.model';

export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];
export const periodKey = (period: ReportingPeriod): string => `${period.year}-${period.month}`;
export const periodLabel = (period: ReportingPeriod): string =>
  `${MONTH_NAMES[period.month - 1]} ${period.year}`;
export const validPeriod = ({ month, year }: ReportingPeriod): boolean =>
  Number.isInteger(month) &&
  month >= 1 &&
  month <= 12 &&
  Number.isInteger(year) &&
  year >= 2000 &&
  year <= 2100;
export function sixMonths(period: ReportingPeriod): ReportingPeriod[] {
  const end = period.year * 12 + period.month - 1;
  return Array.from({ length: 6 }, (_, index) => {
    const serial = end - 5 + index;
    return { month: (serial % 12) + 1, year: Math.floor(serial / 12) };
  });
}
const FALLBACK_COLORS = ['var(--chart-orange)', 'var(--chart-tangerine)', 'var(--chart-yellow)', 'var(--chart-red)', 'var(--chart-income)', 'var(--chart-complement)'];
export function categoryColor(color: string | null, index: number): string {
  return color && /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(color)
    ? color
    : FALLBACK_COLORS[index % FALLBACK_COLORS.length];
}

export class UnsupportedFinanceNumber extends Error {}
export function checkedDashboard(
  data: DashboardResponse,
  period: ReportingPeriod,
): DashboardResponse {
  if (data.month !== period.month || data.year !== period.year)
    throw new Error('Unexpected reporting period');
  const money = [
    data.summary.totalIncome,
    data.summary.totalExpenses,
    data.summary.netSavings,
    ...data.expensesByCategory.map((row) => row.amount),
    ...data.incomeByCategory.map((row) => row.amount),
    ...data.topSpendingCategories.map((row) => row.amount),
    ...data.dailySpendingTrend.map((row) => row.amount),
    ...data.recentTransactions.map((row) => row.amount),
    ...data.budgetUtilization.flatMap((row) => [
      row.monthlyLimit,
      row.spentAmount,
      row.remainingAmount,
    ]),
  ];
  // Bound JSON-number money before presentation; the backend supports a larger decimal range.
  // At this bound, binary representation error remains well below half a cent.
  if (
    money.some(
      (value) => typeof value !== 'number' || !Number.isFinite(value) || Math.abs(value) > 1e12,
    ) ||
    [data.summary.savingsRate, ...data.budgetUtilization.map((row) => row.percentageUsed)].some(
      (value) => value !== null && (typeof value !== 'number' || !Number.isFinite(value)),
    )
  ) {
    throw new UnsupportedFinanceNumber();
  }
  return data;
}

export function expenseSlices(rows: CategoryBreakdown[]) {
  const positive = rows.filter((row) => row.amount > 0);
  // Integer cents and BigInt remainders avoid floating-point drift in rounded percentages.
  const amounts = positive.map((row) => BigInt(Math.round(row.amount * 100)));
  const total = amounts.reduce((sum, amount) => sum + amount, 0n);
  if (total === 0n) return [];
  const units = amounts.map((amount) => Number((amount * 10000n) / total));
  const order = amounts
    .map((amount, index) => ({ index, remainder: (amount * 10000n) % total }))
    .sort((a, b) =>
      a.remainder > b.remainder ? -1 : a.remainder < b.remainder ? 1 : a.index - b.index,
    );
  const missing = 10000 - units.reduce((sum, value) => sum + value, 0);
  for (let index = 0; index < missing; index++) units[order[index].index]++;
  let stop = 0;
  return positive.map((row, index) => {
    const start = stop;
    stop += units[index];
    return {
      ...row,
      color: categoryColor(row.color, index),
      percent: units[index] / 100,
      start: start / 100,
      end: stop / 100,
    };
  });
}
export function financeError(error: unknown): string {
  if (error instanceof UnsupportedFinanceNumber)
    return 'A financial value exceeds the supported display range. Please contact support.';
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0)
      return 'Unable to reach the finance API. Check your connection and try again.';
    if (error.status === 401) return 'Your session has ended. Please sign in again.';
    if (error.status === 403) return 'You do not have permission to view this financial data.';
    if (error.status === 400)
      return 'The reporting period could not be loaded. Choose a month and year from 2000 to 2100.';
  }
  return 'Financial data could not be loaded. Please try again.';
}
