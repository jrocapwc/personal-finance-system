import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { DashboardService } from './dashboard';
import { CategoryService } from './category';
import { AuthService } from './auth';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { testCredentials, testLogin } from '../auth.testing';
import { emptyDashboard } from '../../features/dashboard/dashboard.testing';

describe('Finance API services', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush(testLogin);
  });
  afterEach(() => http.verify());
  it('requests only the exact dashboard URL and period with the existing JWT', () => {
    const data = emptyDashboard({ month: 1, year: 2026 });
    let response: unknown;
    TestBed.inject(DashboardService)
      .getMonthly(data)
      .subscribe((value) => (response = value));
    const request = http.expectOne('/api/v1/dashboard?month=1&year=2026');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['month', 'year']);
    expect(request.request.headers.get('Authorization')).toBeNull();
    expect(request.request.transferCache).toBe(false);
    request.flush(data);
    expect(response).toEqual(data);
  });
  it('loads categories with no extra query, body or transaction/budget requests', () => {
    TestBed.inject(CategoryService).list().subscribe();
    const request = http.expectOne('/api/v1/categories');
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.transferCache).toBe(false);
    expect(request.request.headers.get('Authorization')).toBeNull();
    request.flush([]);
  });
  it('propagates API errors for the dashboard to handle', () => {
    let status = 0;
    TestBed.inject(DashboardService)
      .getMonthly({ month: 1, year: 2026 })
      .subscribe({ error: (error) => (status = error.status) });
    http
      .expectOne('/api/v1/dashboard?month=1&year=2026')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(status).toBe(503);
  });
});
