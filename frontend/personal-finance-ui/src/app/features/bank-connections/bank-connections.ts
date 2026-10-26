import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import {
  BankService,
  DemoConnection,
  ImportResult,
  StatementPreview,
  StatementRow,
} from '../../core/services/bank';
import { CategoryService } from '../../core/services/category';
import { CategoryResponse } from '../../core/models/category.model';
@Component({
  selector: 'app-bank-connections',
  standalone: true,
  imports: [FormsModule, DatePipe, MatButtonModule, MatIconModule],
  templateUrl: './bank-connections.html',
  styleUrl: './bank-connections.scss',
})
export class BankConnections {
  private readonly api = inject(BankService);
  private readonly destroy = inject(DestroyRef);
  readonly connection = signal<DemoConnection | null>(null);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly headers = signal<string[]>([]);
  readonly preview = signal<StatementPreview | null>(null);
  readonly result = signal<ImportResult | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  file: File | null = null;
  mode = 'signed';
  dateConfirmed = false;
  mapping: Record<string, string | number> = {
    date: 0,
    description: 1,
    amount: 2,
    debit: -1,
    credit: -1,
    reference: -1,
    currency: -1,
    dateFormat: 'ISO',
    account: '',
  };
  readonly fields = [
    ['date', 'Transaction date'],
    ['description', 'Description'],
    ['amount', 'Signed amount'],
    ['debit', 'Debit'],
    ['credit', 'Credit'],
    ['reference', 'Bank transaction reference (optional)'],
    ['currency', 'Currency (optional; PHP only)'],
  ];
  constructor() {
    this.run(this.api.connection(), (value) => this.connection.set(value));
    inject(CategoryService)
      .list()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (rows) => this.categories.set(rows),
        error: () => this.error.set('Unable to load categories. Reload the page.'),
      });
  }
  private run<T>(request: Observable<T>, next: (value: T) => void) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    request
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next,
        error: (error: { error?: { message?: string } }) =>
          this.error.set(
            error.error?.message ?? 'Request failed. Check your session and try again.',
          ),
      });
  }
  connect() {
    this.run(this.api.connect(), (value) => this.connection.set(value));
  }
  disconnect() {
    this.run(this.api.disconnect(), (value) => this.connection.set(value));
  }
  sync() {
    this.result.set(null);
    this.run(this.api.sync(), (value) => {
      this.result.set(value);
      this.api
        .connection()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe((value) => this.connection.set(value));
    });
  }
  selectFile(event: Event) {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    this.headers.set([]);
    this.result.set(null);
    this.dateConfirmed = false;
    if (!this.file) return;
    if (this.file.size > 1024 * 1024 || this.file.size === 0) {
      this.error.set('Choose a CSV between 1 byte and 1 MiB.');
      this.file = null;
      return;
    }
    this.run(this.api.columns(this.file), (headers) => {
      this.headers.set(headers);
      const aliases: Record<string, string[]> = {
        date: ['date', 'transaction date', 'transaction_date'],
        description: ['description', 'details', 'memo'],
        amount: ['amount', 'signed amount'],
        debit: ['debit', 'withdrawal'],
        credit: ['credit', 'deposit'],
        reference: ['reference', 'transaction id', 'transaction_id'],
        currency: ['currency'],
      };
      for (const [key, names] of Object.entries(aliases))
        this.mapping[key] = headers.findIndex((header) =>
          names.includes(header.trim().toLowerCase()),
        );
      this.mode = Number(this.mapping['amount']) >= 0 ? 'signed' : 'debit-credit';
    });
  }
  buildPreview() {
    if (!this.file || !this.dateConfirmed) return;
    const mapping = { ...this.mapping };
    if (this.mode === 'signed') {
      mapping['debit'] = -1;
      mapping['credit'] = -1;
    } else mapping['amount'] = -1;
    this.result.set(null);
    this.run(this.api.preview(this.file, mapping), (value) => {
      value.rows.forEach((row) => {
        row.include = !row.validationError && !row.duplicate;
        row.categoryId = null;
        const description = row.description?.toLowerCase() ?? '';
        row.suggestedCategoryId =
          this.choices(row).find((category) => description.includes(category.name.toLowerCase()))
            ?.id ?? null;
      });
      this.preview.set(value);
    });
  }
  choices(row: StatementRow) {
    return this.categories().filter((category) => category.type === row.type);
  }
  suggestionName(row: StatementRow) {
    return (
      this.categories().find((category) => category.id === row.suggestedCategoryId)?.name ??
      'No suggestion'
    );
  }
  ready() {
    const selected = this.preview()?.rows.filter((row) => row.include) ?? [];
    return (
      selected.length > 0 &&
      selected.every((row) => row.categoryId && !row.validationError && !row.duplicate)
    );
  }
  confirm() {
    const preview = this.preview();
    if (!preview || !this.ready()) return;
    this.run(this.api.confirm(preview), (result) => {
      this.result.set(result);
      this.clear();
    });
  }
  cancel() {
    const preview = this.preview();
    if (preview) this.run(this.api.cancel(preview.id), () => this.clear());
  }
  private clear() {
    this.preview.set(null);
    this.file = null;
    this.headers.set([]);
  }
}
