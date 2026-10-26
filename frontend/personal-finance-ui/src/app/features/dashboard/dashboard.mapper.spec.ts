import {
  checkedDashboard,
  expenseSlices,
  categoryColor,
  sixMonths,
  validPeriod,
  UnsupportedFinanceNumber,
} from './dashboard.mapper';
import { populatedDashboard } from './dashboard.testing';

describe('Dashboard mapping', () => {
  const period = { month: 1, year: 2026 };
  it('builds a chronological six-month window over year rollover', () => {
    expect(sixMonths(period)).toEqual([
      { month: 8, year: 2025 },
      { month: 9, year: 2025 },
      { month: 10, year: 2025 },
      { month: 11, year: 2025 },
      { month: 12, year: 2025 },
      { month: 1, year: 2026 },
    ]);
  });
  it('distinguishes unsupported years at the lower boundary', () => {
    expect(sixMonths({ month: 1, year: 2000 }).filter(validPeriod)).toEqual([
      { month: 1, year: 2000 },
    ]);
    expect(validPeriod({ month: 1.5, year: 2026 })).toBe(false);
  });
  it('rounds decimal category amounts to percentages totaling exactly 100%', () => {
    const rows = [0.1, 0.1, 0.1].map((amount, index) => ({
      categoryId: index,
      categoryName: `Item ${index}`,
      color: null,
      amount,
    }));
    const result = expenseSlices(rows);
    expect(result.map((row) => row.percent)).toEqual([33.34, 33.33, 33.33]);
    expect(result.at(-1)?.end).toBe(100);
    expect(result.reduce((sum, row) => sum + Math.round(row.percent * 100), 0)).toBe(10000);
  });
  it('uses real proportions rather than sample chart stops', () => {
    const rows = [0.1, 0.3].map((amount, index) => ({
      categoryId: index,
      categoryName: 'Item',
      color: null,
      amount,
    }));
    expect(expenseSlices(rows).map((row) => row.percent)).toEqual([25, 75]);
    expect(expenseSlices([])).toEqual([]);
  });
  it('accepts only safe backend hex colors and otherwise uses a stable palette', () => {
    expect(categoryColor('#AbC', 0)).toBe('#AbC');
    expect(categoryColor('#123456', 0)).toBe('#123456');
    for (const color of [null, 'red', 'url(https://example.com)', '<script>', '#1234']) {
      expect(categoryColor(color, 0)).toBe('var(--chart-orange)');
    }
  });
  it('preserves decimals, negative savings and null savings rate', () => {
    const data = populatedDashboard(period);
    data.summary.savingsRate = null;
    expect(checkedDashboard(data, period)).toBe(data);
    expect(data.summary.netSavings).toBe(-200.25);
  });
  it('rejects nonfinite and unsupported-range amounts rather than displaying rounded records', () => {
    for (const value of [NaN, Infinity, 1e12 + 1]) {
      const data = populatedDashboard(period);
      data.summary.totalIncome = value;
      expect(() => checkedDashboard(data, period)).toThrow(UnsupportedFinanceNumber);
    }
  });
  it('rejects a response for a different reporting month', () => {
    expect(() => checkedDashboard(populatedDashboard(period), { month: 2, year: 2026 })).toThrow();
  });
});
