import { Component, computed, effect, inject, signal, ViewChild } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CategoryService } from '../../core/services/category';
import { TransactionService } from '../../core/services/transaction';
import { CategoryType } from '../../core/models/category.model';
import {
  TransactionQuery,
  TransactionResponse,
  TransactionSort,
} from '../../core/models/transaction.model';
import { FinanceDialog } from '../finance/finance-dialog';
import { FinancePage } from '../finance/finance-page';
import { financeResource } from '../finance/finance-resource';
import { calendarDate, dateRange, positiveMoney } from '../finance/finance-form';
@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, FinanceDialog],
  templateUrl: './transactions.html',
  styleUrl: '../finance/finance-page.scss',
})
export class Transactions extends FinancePage {
  private readonly api = inject(TransactionService);
  private readonly categoryApi = inject(CategoryService);
  private readonly fb = inject(FormBuilder);
  readonly query = signal<TransactionQuery>({
    page: 0,
    size: 20,
    sort: 'transactionDate',
    direction: 'DESC',
  });
  readonly resource = financeResource(this.query, (query) => this.api.list(query));
  readonly categoryResource = financeResource(signal(null), () => this.categoryApi.list());
  readonly filters = this.fb.group(
    {
      type: this.fb.nonNullable.control<CategoryType | ''>(''),
      categoryId: this.fb.control<number | null>(null),
      startDate: this.fb.nonNullable.control('', calendarDate),
      endDate: this.fb.nonNullable.control('', calendarDate),
      keyword: this.fb.nonNullable.control('', Validators.maxLength(255)),
      sort: this.fb.nonNullable.control<TransactionSort>('transactionDate'),
      direction: this.fb.nonNullable.control<'ASC' | 'DESC'>('DESC'),
      size: this.fb.nonNullable.control(20, [Validators.min(1), Validators.max(100)]),
    },
    { validators: dateRange },
  );
  readonly form = this.fb.group({
    type: this.fb.nonNullable.control<CategoryType>('EXPENSE', Validators.required),
    categoryId: this.fb.control<number | null>(null, Validators.required),
    amount: this.fb.nonNullable.control('', [Validators.required, positiveMoney]),
    transactionDate: this.fb.nonNullable.control('', [Validators.required, calendarDate]),
    description: this.fb.nonNullable.control('', Validators.maxLength(255)),
  });
  readonly selectedType = toSignal(this.form.controls.type.valueChanges, {
    initialValue: 'EXPENSE' as CategoryType,
  });
  readonly filterType = toSignal(this.filters.controls.type.valueChanges, {
    initialValue: '' as CategoryType | '',
  });
  readonly formCategories = computed(() =>
    (this.categoryResource.data() ?? []).filter((row) => row.type === this.selectedType()),
  );
  readonly filterCategories = computed(() =>
    (this.categoryResource.data() ?? []).filter(
      (row) => !this.filterType() || row.type === this.filterType(),
    ),
  );
  readonly editingId = signal<number | null>(null);
  readonly deleting = signal<TransactionResponse | null>(null);
  readonly sortLabels: Record<TransactionSort, string> = {
    transactionDate: 'Transaction date',
    amount: 'Amount',
    createdAt: 'Created',
    updatedAt: 'Updated',
    description: 'Description',
    type: 'Type',
    id: 'ID',
  };
  readonly sortFields: TransactionSort[] = [
    'transactionDate',
    'amount',
    'createdAt',
    'updatedAt',
    'description',
    'type',
    'id',
  ];
  @ViewChild('editor') editor?: FinanceDialog;
  @ViewChild('confirmation') confirmation?: FinanceDialog;
  constructor() {
    super();
    this.form.controls.type.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.categoryId.setValue(null));
    this.filters.controls.type.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.filters.controls.categoryId.setValue(null));
    effect(() => {
      const data = this.resource.data();
      if (data && data.page > 0 && !data.content.length && data.page >= data.totalPages)
        this.query.update((query) => ({ ...query, page: Math.max(0, data.totalPages - 1) }));
    });
  }
  apply(): void {
    this.filters.markAllAsTouched();
    if (this.filters.invalid) return;
    const values = this.filters.getRawValue();
    this.query.set({
      ...values,
      type: values.type || undefined,
      categoryId: values.categoryId ?? undefined,
      startDate: values.startDate || undefined,
      endDate: values.endDate || undefined,
      keyword: values.keyword.trim() || undefined,
      page: 0,
    });
  }
  clearFilters(): void {
    this.filters.reset({
      type: '',
      categoryId: null,
      startDate: '',
      endDate: '',
      keyword: '',
      sort: 'transactionDate',
      direction: 'DESC',
      size: 20,
    });
    this.apply();
  }
  changePage(offset: number): void {
    const data = this.resource.data();
    if (!data) return;
    const next = data.page + offset;
    if (next >= 0 && next < data.totalPages)
      this.query.update((query) => ({ ...query, page: next }));
  }
  categoryName(id: number): string {
    return (
      this.categoryResource.data()?.find((row) => row.id === id)?.name ??
      `Category #${id} (name unavailable)`
    );
  }
  add(): void {
    this.editingId.set(null);
    this.form.reset({
      type: 'EXPENSE',
      categoryId: null,
      amount: '',
      transactionDate: this.today(),
      description: '',
    });
    this.editorError.set('');
    this.editor?.open();
  }
  edit(row: TransactionResponse): void {
    this.editingId.set(row.id);
    this.form.reset({
      type: row.type,
      categoryId: null,
      amount: String(row.amount),
      transactionDate: row.transactionDate,
      description: row.description ?? '',
    });
    this.form.controls.categoryId.setValue(row.categoryId);
    this.editorError.set('');
    this.editor?.open();
  }
  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const values = this.form.getRawValue();
    if (!this.formCategories().some((row) => row.id === values.categoryId)) {
      this.form.controls.categoryId.setErrors({ category: true });
      return;
    }
    const body = {
      ...values,
      categoryId: values.categoryId!,
      amount: Number(values.amount),
      description: values.description || null,
    };
    const id = this.editingId();
    this.mutate(id === null ? this.api.create(body) : this.api.update(id, body), () => {
      this.editor?.close();
      this.success.set(id === null ? 'Transaction created.' : 'Transaction updated.');
    });
  }
  askDelete(row: TransactionResponse): void {
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
        this.success.set('Transaction deleted.');
      },
      true,
    );
  }
  protected clearSessionForms(): void {
    this.query.set({ page: 0, size: 20, sort: 'transactionDate', direction: 'DESC' });
    this.form.reset();
    this.filters.reset({
      type: '',
      categoryId: null,
      startDate: '',
      endDate: '',
      keyword: '',
      sort: 'transactionDate',
      direction: 'DESC',
      size: 20,
    });
    this.editingId.set(null);
    this.deleting.set(null);
    this.editor?.close();
    this.confirmation?.close();
  }
  private today(): string {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
