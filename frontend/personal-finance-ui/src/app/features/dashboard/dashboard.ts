import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth';
import { DashboardStore } from './dashboard.store';
import { expenseSlices, MONTH_NAMES } from './dashboard.mapper';
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, ReactiveFormsModule, MatCardModule, MatIconModule],
  providers: [DashboardStore],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.scss', './dashboard-controls.scss'],
})
export class Dashboard {
  readonly store = inject(DashboardStore);
  private readonly auth = inject(AuthService);
  readonly firstName = computed(
    () => this.auth.currentUser()?.name.trim().split(/\s+/)[0] || 'there',
  );
  readonly currencyCode = 'PHP';
  readonly monthNames = MONTH_NAMES;
  readonly controls = inject(FormBuilder).nonNullable.group({
    month: [
      this.store.period().month,
      [Validators.required, Validators.min(1), Validators.max(12)],
    ],
    year: [
      this.store.period().year,
      [
        Validators.required,
        Validators.min(2000),
        Validators.max(2100),
        Validators.pattern(/^\d{4}$/),
      ],
    ],
  });
  readonly summaries = computed(() => {
    const summary = this.store.data()?.summary;
    return summary
      ? [
          {
            label: 'Monthly Income',
            amount: summary.totalIncome,
            kind: 'income',
            icon: 'trending_up',
            rate: false,
          },
          {
            label: 'Monthly Expenses',
            amount: summary.totalExpenses,
            kind: 'expense',
            icon: 'shopping_cart',
            rate: false,
          },
          {
            label: 'Net Savings',
            amount: summary.netSavings,
            kind: 'balance',
            icon: 'savings',
            rate: false,
          },
          {
            label: 'Savings Rate',
            amount: summary.savingsRate,
            kind: 'savings',
            icon: 'percent',
            rate: true,
          },
        ]
      : [];
  });
  readonly expenses = computed(() => expenseSlices(this.store.data()?.expensesByCategory ?? []));
  readonly donutBackground = computed(() =>
    this.expenses().length
      ? `conic-gradient(${this.expenses()
          .map((row) => `${row.color} ${row.start}% ${row.end}%`)
          .join(', ')})`
      : 'var(--track-bg)',
  );
  readonly donutDescription = computed(
    () =>
      this.expenses()
        .map((row) => `${row.categoryName}: ${row.percent}%`)
        .join(', ') || 'No expenses in this reporting period',
  );
  readonly historyLoading = computed(() =>
    this.store.state().history.some((month) => month.status === 'loading'),
  );
  readonly historyIncomplete = computed(() =>
    this.store
      .state()
      .history.some((month) => month.status === 'error' || month.status === 'unsupported'),
  );
  readonly chartMax = computed(() => {
    const values = this.store
      .state()
      .history.flatMap((month) =>
        month.summary ? [month.summary.totalIncome, month.summary.totalExpenses] : [],
      );
    const maximum = Math.max(0, ...values);
    if (!maximum) return 100;
    const unit = 10 ** Math.floor(Math.log10(maximum));
    return Math.ceil(maximum / unit) * unit;
  });
  readonly chartTicks = computed(() => [
    this.chartMax(),
    (this.chartMax() * 2) / 3,
    this.chartMax() / 3,
    0,
  ]);
  readonly chartDescription = computed(() =>
    this.store
      .state()
      .history.map(
        (month) =>
          `${month.label}: ${month.summary ? `income PHP ${month.summary.totalIncome}, expenses PHP ${month.summary.totalExpenses}` : 'unavailable'}`,
      )
      .join('; '),
  );
  readonly categories = computed(() => {
    const data = this.store.data();
    return new Map([
      ...(data
        ? [...data.incomeByCategory, ...data.expensesByCategory].map(
            (row) => [row.categoryId, row.categoryName] as const,
          )
        : []),
      ...this.store.state().categories.map((row) => [row.id, row.name] as const),
    ]);
  });
  readonly transactions = computed(() =>
    (this.store.data()?.recentTransactions ?? []).map((row) => ({
      ...row,
      category: this.categoryName(row.categoryId),
      signedAmount: row.type === 'EXPENSE' ? -row.amount : row.amount,
      icon: row.type === 'EXPENSE' ? 'shopping_bag' : 'payments',
    })),
  );
  readonly budgets = computed(() =>
    (this.store.data()?.budgetUtilization ?? []).map((row) => ({
      ...row,
      label: this.categoryName(row.categoryId),
      width: Math.max(0, Math.min(100, row.percentageUsed)),
    })),
  );
  constructor() {
    this.controls.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.controls.valid) this.store.setPeriod(this.controls.getRawValue());
    });
  }
  private categoryName(id: number): string {
    return this.categories().get(id) ?? `Category #${id} (name unavailable)`;
  }
}
