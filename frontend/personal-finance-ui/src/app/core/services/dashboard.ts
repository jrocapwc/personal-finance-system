import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardResponse, ReportingPeriod } from '../models/dashboard.model';
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  getMonthly({ month, year }: ReportingPeriod): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>('/api/v1/dashboard', {
      params: { month, year },
      transferCache: false,
    });
  }
}
