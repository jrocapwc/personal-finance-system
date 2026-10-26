import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { FinanceChanges } from './finance-changes';
export interface DemoConnection {
  providerId: string;
  provider: string;
  accountName: string;
  maskedAccountNumber: string;
  simulated: boolean;
  connected: boolean;
  demoBalance: string;
  currency: string;
  lastSync: string | null;
}
export interface ImportResult {
  importedCount: number;
  skippedCount: number;
  failedCount: number;
  totalProcessed: number;
}
export interface StatementRow {
  rowNumber: number;
  transactionDate: string | null;
  description: string | null;
  type: string | null;
  amount: string | null;
  validationError: string | null;
  duplicate: boolean;
  suggestedCategoryId: number | null;
  include?: boolean;
  categoryId?: number | null;
}
export interface StatementPreview {
  id: string;
  expiresAt: string;
  headers: string[];
  rows: StatementRow[];
}
@Injectable({ providedIn: 'root' })
export class BankService {
  private readonly http = inject(HttpClient);
  private readonly changes = inject(FinanceChanges);
  private readonly base = '/api/v1/bank-connections';
  connection() {
    return this.http.get<DemoConnection>(this.base, { transferCache: false });
  }
  connect() {
    return this.http.post<DemoConnection>(`${this.base}/demo`, {});
  }
  disconnect() {
    return this.http.delete<DemoConnection>(`${this.base}/demo`);
  }
  sync() {
    return this.http
      .post<ImportResult>(`${this.base}/demo/sync`, {})
      .pipe(tap(this.changes.track()));
  }
  columns(file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<string[]>(`${this.base}/statements/columns`, form);
  }
  preview(file: File, mapping: Record<string, string | number>) {
    const form = new FormData();
    form.append('file', file);
    for (const [key, value] of Object.entries(mapping)) form.append(key, String(value));
    return this.http.post<StatementPreview>(`${this.base}/statements/preview`, form);
  }
  confirm(preview: StatementPreview) {
    return this.http
      .post<ImportResult>(`${this.base}/statements/${preview.id}/confirm`, {
        rows: preview.rows
          .filter((row) => row.include)
          .map(({ rowNumber, categoryId }) => ({ rowNumber, categoryId })),
      })
      .pipe(tap(this.changes.track()));
  }
  cancel(id: string) {
    return this.http.delete<void>(`${this.base}/statements/${id}`);
  }
}
