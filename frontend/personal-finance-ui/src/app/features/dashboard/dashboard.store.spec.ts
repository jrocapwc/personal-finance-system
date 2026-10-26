import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { DashboardStore } from './dashboard.store';
import { FinanceChanges } from '../../core/services/finance-changes';
import { AuthService } from '../../core/services/auth';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { testCredentials, testLogin, testUser } from '../../core/auth.testing';
import { ReportingPeriod } from '../../core/models/dashboard.model';
import { emptyDashboard, populatedDashboard } from './dashboard.testing';

describe('DashboardStore', () => {
  let http: HttpTestingController;
  let store: DashboardStore;
  const selected = { month: 1, year: 2026 };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardStore,
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    try {
      http.verify({ ignoreCancelled: true });
    } finally {
      TestBed.resetTestingModule();
    }
  });
  function start() {
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ ...testLogin, user: { ...testUser } });
    store = TestBed.inject(DashboardStore);
    store.setPeriod(selected);
    TestBed.tick();
  }
  function period(request: TestRequest): ReportingPeriod {
    return {
      month: Number(request.request.params.get('month')),
      year: Number(request.request.params.get('year')),
    };
  }
  function finishHistory() {
    for (const request of http.match((request) => request.url === '/api/v1/dashboard')) {
      if (!request.cancelled) request.flush(emptyDashboard(period(request)));
    }
  }
  function current() {
    return http.expectOne('/api/v1/dashboard?month=1&year=2026');
  }
  it('does not request finance data without an authenticated user', () => {
    store = TestBed.inject(DashboardStore);
    TestBed.tick();
    http.expectNone((request) => request.url.startsWith('/api/'));
    expect(store.data()).toBeNull();
  });
  it('never loads authenticated data during server rendering', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        DashboardStore,
        { provide: PLATFORM_ID, useValue: 'server' },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    start();
    http.expectNone(
      (request) => request.url === '/api/v1/dashboard' || request.url === '/api/v1/categories',
    );
    expect(store.data()).toBeNull();
    expect(store.state().loading).toBe(false);
  });
  it('loads six months once each and reuses the selected response immediately', () => {
    start();
    http.expectOne('/api/v1/categories').flush([]);
    const requests = http.match((request) => request.url === '/api/v1/dashboard');
    expect(requests).toHaveLength(6);
    expect(new Set(requests.map((request) => request.request.urlWithParams)).size).toBe(6);
    const selectedRequest = requests.find((request) => period(request).month === 1)!;
    selectedRequest.flush(populatedDashboard(selected));
    expect(store.data()?.summary.netSavings).toBe(-200.25);
    expect(store.state().loading).toBe(false);
    for (const request of requests.filter((request) => request !== selectedRequest))
      request.flush(emptyDashboard(period(request)));
    expect(store.state().history.map((row) => row.year)).toEqual([
      2025, 2025, 2025, 2025, 2025, 2026,
    ]);
    expect(store.state().history.every((row) => row.status === 'ready')).toBe(true);
  });
  it('shows valid zero income, empty lists and unavailable savings rate', () => {
    start();
    current().flush(emptyDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()?.summary).toEqual({
      totalIncome: 0,
      totalExpenses: 0,
      netSavings: 0,
      savingsRate: null,
    });
    expect(store.state().error).toBe('');
  });
  it('isolates a historical failure from successful selected-month data', () => {
    start();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    http
      .expectOne('/api/v1/dashboard?month=12&year=2025')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    finishHistory();
    expect(store.data()?.summary.totalIncome).toBe(1000.1);
    expect(store.state().history.find((row) => row.month === 12)?.status).toBe('error');
    expect(store.state().history.find((row) => row.month === 12)?.summary).toBeNull();
  });
  it('reports a selected-month failure with no fallback records, then retries', () => {
    start();
    current().flush({ message: 'private server detail' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()).toBeNull();
    expect(store.state().error).toContain('could not be loaded');
    expect(store.state().error).not.toContain('private');
    store.refresh();
    TestBed.tick();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()).toBeTruthy();
  });
  it('allows category lookup failures without losing the dashboard', () => {
    start();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush({}, { status: 500, statusText: 'Error' });
    finishHistory();
    expect(store.data()).toBeTruthy();
    expect(store.state().categoryError).toContain('Category names could not');
  });
  it('cancels all requests and rejects stale values on rapid period changes', () => {
    start();
    const old = http.match(() => true);
    store.setPeriod({ month: 2, year: 2026 });
    expect(store.data()).toBeNull();
    TestBed.tick();
    expect(old.every((request) => request.cancelled)).toBe(true);
    const next = http.match(() => true);
    store.setPeriod({ month: 3, year: 2026 });
    TestBed.tick();
    expect(next.every((request) => request.cancelled)).toBe(true);
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()?.month).toBe(3);
  });
  it('rejects an old response that races before period cancellation runs', () => {
    start();
    const lookup = http.expectOne('/api/v1/categories');
    const old = current();
    store.setPeriod({ month: 2, year: 2026 });
    old.flush(populatedDashboard(selected));
    expect(store.data()).toBeNull();
    TestBed.tick();
    expect(lookup.cancelled).toBe(true);
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()?.month).toBe(2);
  });
  it('hides loaded data immediately when the selected period changes', () => {
    start();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    store.setPeriod({ month: 2, year: 2026 });
    expect(store.data()).toBeNull();
    TestBed.tick();
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()?.month).toBe(2);
  });
  it('clears visible data and cancels requests when logout occurs mid-load', () => {
    start();
    current().flush(populatedDashboard(selected));
    const pending = http.match(() => true);
    TestBed.inject(AuthService).logout();
    expect(store.data()).toBeNull();
    TestBed.tick();
    expect(pending.every((request) => request.cancelled)).toBe(true);
    expect(store.state().categories).toEqual([]);
  });
  it('rejects an old response that races with logout before cancellation runs', () => {
    start();
    const old = current();
    TestBed.inject(AuthService).logout();
    old.flush(populatedDashboard(selected));
    expect(store.data()).toBeNull();
    TestBed.tick();
    expect(
      http
        .match((request) => request.url === '/api/v1/dashboard')
        .every((request) => request.cancelled),
    ).toBe(true);
  });
  it('does not reuse private data after another login session', () => {
    start();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    expect(store.data()).toBeNull();
    http
      .expectOne('/api/v1/auth/login')
      .flush({ ...testLogin, user: { ...testUser, id: 'another-user' } });
    TestBed.tick();
    expect(store.data()).toBeNull();
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    expect(store.data()?.summary.totalIncome).toBe(0);
  });
  it('handles a rejected JWT by clearing data through the existing interceptor', () => {
    start();
    current().flush({}, { status: 401, statusText: 'Unauthorized' });
    TestBed.tick();
    expect(store.data()).toBeNull();
    expect(TestBed.inject(AuthService).currentUser()).toBeNull();
  });
  it('marks unsupported historical years without requesting them', () => {
    start();
    for (const request of http.match(() => true))
      request.flush(
        request.request.url === '/api/v1/categories' ? [] : emptyDashboard(period(request)),
      );
    store.setPeriod({ month: 1, year: 2000 });
    TestBed.tick();
    http.expectOne('/api/v1/categories').flush([]);
    const requests = http.match((request) => request.url === '/api/v1/dashboard');
    expect(requests).toHaveLength(1);
    requests[0].flush(emptyDashboard({ month: 1, year: 2000 }));
    expect(store.state().history.filter((row) => row.status === 'unsupported')).toHaveLength(5);
  });
  it('reloads all six reports and category names after finance mutations', () => {
    start();
    current().flush(populatedDashboard(selected));
    http.expectOne('/api/v1/categories').flush([]);
    finishHistory();
    TestBed.inject(FinanceChanges).track()();
    TestBed.tick();
    const reports = http.match((request) => request.url === '/api/v1/dashboard');
    expect(reports).toHaveLength(6);
    for (const request of reports) request.flush(emptyDashboard(period(request)));
    http.expectOne('/api/v1/categories').flush([]);
    expect(store.data()?.summary.totalIncome).toBe(0);
  });
  it('cancels pending finance requests when the feature is destroyed', () => {
    start();
    const pending = http.match(() => true);
    TestBed.resetTestingModule();
    expect(pending.every((request) => request.cancelled)).toBe(true);
  });
});
