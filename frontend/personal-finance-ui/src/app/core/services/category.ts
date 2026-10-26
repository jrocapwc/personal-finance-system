import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { CategoryRequest, CategoryResponse, CategoryType } from '../models/category.model';
import { checkedFinance, safeId } from '../models/finance-safety';
import { FinanceChanges } from './finance-changes';
@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly changes = inject(FinanceChanges);
  list(type?: CategoryType): Observable<CategoryResponse[]> {
    return this.http
      .get<CategoryResponse[]>('/api/v1/categories', {
        params: type ? { type } : {},
        transferCache: false,
      })
      .pipe(map(checkedFinance));
  }
  create({ name, type, color }: CategoryRequest): Observable<CategoryResponse> {
    return this.http
      .post<CategoryResponse>('/api/v1/categories', { name, type, color })
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  update(id: number, { name, type, color }: CategoryRequest): Observable<CategoryResponse> {
    return this.http
      .put<CategoryResponse>(`/api/v1/categories/${safeId(id)}`, { name, type, color })
      .pipe(tap(this.changes.track()), map(checkedFinance));
  }
  delete(id: number): Observable<void> {
    return this.http
      .delete<void>(`/api/v1/categories/${safeId(id)}`)
      .pipe(tap(this.changes.track()));
  }
}
