import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import {
  TransactionPageResponse,
  TransactionQuery,
  TransactionRequest,
  TransactionResponse,
} from '../models/transaction.model';
import { checkedFinance, safeAmount, safeId } from '../models/finance-safety';
import { FinanceChanges } from './finance-changes';
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly changes = inject(FinanceChanges);
  list(query: TransactionQuery): Observable<TransactionPageResponse> {
    let params = new HttpParams();
    for (const key of [
      'type',
      'categoryId',
      'startDate',
      'endDate',
      'keyword',
      'page',
      'size',
      'sort',
      'direction',
    ] as const) {
      const value = query[key];
      if (value !== undefined && value !== '')
        params = params.set(key, String(key === 'categoryId' ? safeId(Number(value)) : value));
    }
    return this.http
      .get<TransactionPageResponse>('/api/v1/transactions', { params, transferCache: false })
      .pipe(map(checkedFinance));
  }
  get(id: number): Observable<TransactionResponse> {
    return this.http
      .get<TransactionResponse>(`/api/v1/transactions/${safeId(id)}`, { transferCache: false })
      .pipe(map(checkedFinance));
  }
  create(request: TransactionRequest): Observable<TransactionResponse> {
    return this.http
      .post<TransactionResponse>('/api/v1/transactions', this.body(request))
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  update(id: number, request: TransactionRequest): Observable<TransactionResponse> {
    return this.http
      .put<TransactionResponse>(`/api/v1/transactions/${safeId(id)}`, this.body(request))
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  delete(id: number): Observable<void> {
    return this.http
      .delete<void>(`/api/v1/transactions/${safeId(id)}`)
      .pipe(tap(this.changes.track()));
  }
  private body({
    categoryId,
    type,
    amount,
    description,
    transactionDate,
  }: TransactionRequest): TransactionRequest {
    return {
      categoryId: safeId(categoryId),
      type,
      amount: safeAmount(amount),
      description,
      transactionDate,
    };
  }
}
