import { Component, computed, inject, signal, ViewChild } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BudgetService } from '../../core/services/budget';
import { CategoryService } from '../../core/services/category';
import { BudgetResponse } from '../../core/models/budget.model';
import { FinanceDialog } from '../finance/finance-dialog';
import { FinancePage } from '../finance/finance-page';
import { financeResource } from '../finance/finance-resource';
import { integer, positiveMoney } from '../finance/finance-form';
@Component({
  selector: 'app-budgets',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, ReactiveFormsModule, FinanceDialog],
  templateUrl: './budgets.html',
  styleUrl: '../finance/finance-page.scss',
})
export class Budgets extends FinancePage {
  private readonly api = inject(BudgetService);
  private readonly categoryApi = inject(CategoryService);
  private readonly fb = inject(FormBuilder);
  readonly period = signal({ month: new Date().getMonth() + 1, year: new Date().getFullYear() });
  readonly periodForm = this.fb.nonNullable.group({
    month: [
      this.period().month,
      [Validators.required, Validators.min(1), Validators.max(12), integer],
    ],
    year: [
      this.period().year,
      [Validators.required, Validators.min(2000), Validators.max(2100), integer],
    ],
  });
  readonly resource = financeResource(this.period, (period) =>
    this.api.list(period.month, period.year),
  );
  readonly categoryResource = financeResource(signal(null), () => this.categoryApi.list('EXPENSE'));
  readonly form = this.fb.group({
    categoryId: this.fb.control<number | null>(null, Validators.required),
    month: this.fb.nonNullable.control(this.period().month, [
      Validators.required,
      Validators.min(1),
      Validators.max(12),
      integer,
    ]),
    year: this.fb.nonNullable.control(this.period().year, [
      Validators.required,
      Validators.min(2000),
      Validators.max(2100),
      integer,
    ]),
    monthlyLimit: this.fb.nonNullable.control('', [Validators.required, positiveMoney]),
    alertThreshold: this.fb.nonNullable.control(80, [
      Validators.required,
      Validators.min(1),
      Validators.max(99),
      integer,
    ]),
  });
  readonly editingId = signal<number | null>(null);
  readonly deleting = signal<BudgetResponse | null>(null);
  readonly rows = computed(() =>
    (this.resource.data() ?? []).map((row) => ({
      ...row,
      width: Math.max(0, Math.min(100, row.percentageUsed)),
      label: this.categoryName(row.categoryId),
    })),
  );
  readonly monthNames = [
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
  @ViewChild('editor') editor?: FinanceDialog;
  @ViewChild('confirmation') confirmation?: FinanceDialog;
  applyPeriod(): void {
    this.periodForm.markAllAsTouched();
    if (this.periodForm.valid) this.period.set(this.periodForm.getRawValue());
  }
  categoryName(id: number): string {
    return (
      this.categoryResource.data()?.find((row) => row.id === id)?.name ??
      `Category #${id} (name unavailable)`
    );
  }
  add(): void {
    this.editingId.set(null);
    this.form.reset({ categoryId: null, ...this.period(), monthlyLimit: '', alertThreshold: 80 });
    this.editorError.set('');
    this.editor?.open();
  }
  edit(row: BudgetResponse): void {
    this.editingId.set(row.id);
    this.form.reset({
      categoryId: row.categoryId,
      month: row.month,
      year: row.year,
      monthlyLimit: String(row.monthlyLimit),
      alertThreshold: row.alertThreshold,
    });
    this.editorError.set('');
    this.editor?.open();
  }
  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const values = this.form.getRawValue();
    if (
      !this.categoryResource
        .data()
        ?.some((row) => row.id === values.categoryId && row.type === 'EXPENSE')
    ) {
      this.form.controls.categoryId.setErrors({ category: true });
      return;
    }
    const body = {
      ...values,
      categoryId: values.categoryId!,
      monthlyLimit: Number(values.monthlyLimit),
    };
    const id = this.editingId();
    this.mutate(id === null ? this.api.create(body) : this.api.update(id, body), () => {
      this.editor?.close();
      this.success.set(id === null ? 'Budget created.' : 'Budget updated.');
    });
  }
  askDelete(row: BudgetResponse): void {
    this.deleting.set(row);
    this.deleteError.set('');
    this.confirmation?.open();
  }
  remove(): void {
    const row = this.deleting();
    if (!row || this.busy()) return;
    this.mutate(
      this.api.delete(row.id),
      () => {
        this.confirmation?.close();
        this.deleting.set(null);
        this.success.set('Budget deleted. Transactions and categories were preserved.');
      },
      true,
    );
  }
  protected clearSessionForms(): void {
    this.form.reset();
    this.editingId.set(null);
    this.deleting.set(null);
    this.editor?.close();
    this.confirmation?.close();
  }
}
