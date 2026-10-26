import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { BudgetRequest, BudgetResponse } from '../models/budget.model';
import { checkedFinance, safeId, safeAmount } from '../models/finance-safety';
import { FinanceChanges } from './finance-changes';
@Injectable({ providedIn: 'root' })
export class BudgetService {
  private readonly http = inject(HttpClient);
  private readonly changes = inject(FinanceChanges);
  list(month: number, year: number): Observable<BudgetResponse[]> {
    return this.http
      .get<BudgetResponse[]>('/api/v1/budgets', { params: { month, year }, transferCache: false })
      .pipe(map(checkedFinance));
  }
  get(id: number): Observable<BudgetResponse> {
    return this.http
      .get<BudgetResponse>(`/api/v1/budgets/${safeId(id)}`, { transferCache: false })
      .pipe(map(checkedFinance));
  }
  create(request: BudgetRequest): Observable<BudgetResponse> {
    return this.http
      .post<BudgetResponse>('/api/v1/budgets', this.body(request))
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  update(id: number, request: BudgetRequest): Observable<BudgetResponse> {
    return this.http
      .put<BudgetResponse>(`/api/v1/budgets/${safeId(id)}`, this.body(request))
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/budgets/${safeId(id)}`).pipe(tap(this.changes.track()));
  }
  private body({
    categoryId,
    month,
    year,
    monthlyLimit,
    alertThreshold,
  }: BudgetRequest): BudgetRequest {
    return {
      categoryId: safeId(categoryId),
      month,
      year,
      monthlyLimit: safeAmount(monthlyLimit),
      alertThreshold,
    };
  }
}
