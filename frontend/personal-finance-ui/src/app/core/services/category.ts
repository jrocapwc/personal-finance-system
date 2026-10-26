import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoryResponse } from '../models/category.model';
@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  list(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>('/api/v1/categories', { transferCache: false });
  }
}
