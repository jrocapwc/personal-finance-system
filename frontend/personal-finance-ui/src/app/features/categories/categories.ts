import { Component, inject, signal, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoryService } from '../../core/services/category';
import { CategoryResponse, CategoryType } from '../../core/models/category.model';
import { FinanceDialog } from '../finance/finance-dialog';
import { FinancePage } from '../finance/finance-page';
import { financeResource } from '../finance/finance-resource';
import { nonBlank } from '../finance/finance-form';
@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [ReactiveFormsModule, FinanceDialog],
  templateUrl: './categories.html',
  styleUrl: '../finance/finance-page.scss',
})
export class Categories extends FinancePage {
  private readonly api = inject(CategoryService);
  readonly type = signal<CategoryType | ''>('');
  readonly resource = financeResource(this.type, (type) => this.api.list(type || undefined));
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, nonBlank, Validators.maxLength(100)]],
    type: ['EXPENSE' as CategoryType, Validators.required],
    color: ['', Validators.pattern(/^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/)],
  });
  readonly editingId = signal<number | null>(null);
  readonly deleting = signal<CategoryResponse | null>(null);
  @ViewChild('editor') editor?: FinanceDialog;
  @ViewChild('confirmation') confirmation?: FinanceDialog;
  setFilter(value: string): void {
    this.type.set(value as CategoryType | '');
  }
  add(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', type: 'EXPENSE', color: '' });
    this.editorError.set('');
    this.editor?.open();
  }
  edit(row: CategoryResponse): void {
    this.editingId.set(row.id);
    this.form.reset({ name: row.name, type: row.type, color: row.color ?? '' });
    this.editorError.set('');
    this.editor?.open();
  }
  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const values = this.form.getRawValue();
    const body = { ...values, name: values.name.trim(), color: values.color || null };
    const id = this.editingId();
    this.mutate(id === null ? this.api.create(body) : this.api.update(id, body), () => {
      this.editor?.close();
      this.success.set(id === null ? 'Category created.' : 'Category updated.');
    });
  }
  askDelete(row: CategoryResponse): void {
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
        this.success.set('Category deleted.');
      },
      true,
    );
  }
  color(value: string | null): string {
    return value && /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(value) ? value : 'var(--chart-grey)';
  }
  protected clearSessionForms(): void {
    this.form.reset();
    this.editingId.set(null);
    this.deleting.set(null);
    this.editor?.close();
    this.confirmation?.close();
  }
}
