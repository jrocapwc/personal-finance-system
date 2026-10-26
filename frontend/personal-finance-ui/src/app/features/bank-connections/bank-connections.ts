import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize, switchMap, tap } from 'rxjs';
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
  readonly balanceHidden = signal(false);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly headers = signal<string[]>([]);
  readonly preview = signal<StatementPreview | null>(null);
  readonly result = signal<ImportResult | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('statementFile');
  readonly dragging = signal(false);
  readonly banks = [
    { id: 'bpi', name: 'BPI', subtitle: 'Bank of the Philippine Islands', logo: 'banks/bpi.svg' },
    { id: 'bdo', name: 'BDO', subtitle: 'BDO Unibank', logo: 'banks/bdo.svg' },
    { id: 'metrobank', name: 'Metrobank', subtitle: 'Metrobank', logo: 'banks/metrobank.svg' },
    { id: 'security', name: 'Security Bank', subtitle: 'Security Bank', logo: 'banks/security-bank.svg' },
    { id: 'unionbank', name: 'UnionBank', subtitle: 'UnionBank', logo: 'banks/unionbank.svg' },
    { id: 'other', name: 'Other Bank', subtitle: 'Any compatible bank statement', logo: null },
  ];
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
    // Google's static Material icon: no remote font or uploaded SVG content.
    inject(MatIconRegistry).addSvgIconLiteral(
      'account_balance',
      inject(DomSanitizer).bypassSecurityTrustHtml(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="currentColor" d="M4 10h3v7H4zm6.5 0h3v7h-3zM2 19h20v3H2zm15-9h3v7h-3zM12 1 2 6v2h20V6z"/></svg>',
      ),
    );
    inject(MatIconRegistry).addSvgIconLiteral(
      'check_circle',
      inject(DomSanitizer).bypassSecurityTrustHtml(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="currentColor" d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 15-5-5 1.4-1.4L10 14.2l7.6-7.6L19 8z"/></svg>',
      ),
    );
    this.run(this.api.connection(), (value) => this.connection.set(value));
    inject(CategoryService)
      .list()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (rows) => this.categories.set(rows),
        error: () => this.error.set('Unable to load categories. Reload the page.'),
      });
  }
  providerLogo(providerId: string) {
    return this.banks.find((bank) => bank.id === providerId)?.logo;
  }
  isDemoConnected(providerId: string) {
    const connection = this.connection();
    return connection?.providerId === providerId && connection.simulated && connection.connected;
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
    if (this.busy()) return;
    this.run(this.api.connect(), (value) => this.connection.set(value));
  }
  disconnect() {
    if (this.busy()) return;
    this.run(this.api.disconnect(), (value) => this.connection.set(value));
  }
  sync() {
    if (this.busy()) return;
    this.result.set(null);
    this.run(
      this.api.sync().pipe(
        tap((value) => this.result.set(value)),
        switchMap(() => this.api.connection()),
      ),
      (value) => this.connection.set(value),
    );
  }
  browseFile() {
    if (!this.busy() && !this.preview()) this.fileInput()?.nativeElement.click();
  }
  dragOver(event: DragEvent) {
    event.preventDefault();
    if (!this.busy() && !this.preview()) this.dragging.set(true);
  }
  dropFile(event: DragEvent) {
    event.preventDefault();
    this.dragging.set(false);
    if (this.busy() || this.preview()) return;
    const files = event.dataTransfer?.files;
    if (!files?.length) return;
    if (files.length !== 1) {
      this.error.set('Choose one CSV statement at a time.');
      return;
    }
    this.loadFile(files[0]);
  }
  selectFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    // Allow choosing the same file again after cancellation or a validation error.
    input.value = '';
    if (file && !this.busy() && !this.preview()) this.loadFile(file);
  }
  private loadFile(file: File) {
    this.file = file;
    this.headers.set([]);
    this.error.set('');
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
    this.dragging.set(false);
  }
}
