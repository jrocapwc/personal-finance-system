import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Dashboard } from './dashboard';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth';
import { testCredentials, testLogin } from '../../core/auth.testing';
import { emptyDashboard, populatedDashboard } from './dashboard.testing';

describe('Live dashboard component', () => {
  let fixture: ComponentFixture<Dashboard>;
  let page: Dashboard;
  let http: HttpTestingController;
  const period = { month: 10, year: 2026 };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    fixture = TestBed.createComponent(Dashboard);
    page = fixture.componentInstance;
    page.controls.setValue(period);
    fixture.detectChanges();
    TestBed.tick();
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));
  function finish(data: DashboardResponse, categories: unknown[] = []) {
    http.expectOne('/api/v1/categories').flush(categories);
    for (const request of http.match((request) => request.url === '/api/v1/dashboard')) {
      const selected = request.request.params.get('month') === '10';
      request.flush(
        selected
          ? data
          : emptyDashboard({
              month: Number(request.request.params.get('month')),
              year: Number(request.request.params.get('year')),
            }),
      );
    }
    fixture.detectChanges();
  }
  function text() {
    return fixture.nativeElement.textContent as string;
  }
  it('shows loading without sample records or a fake balance', () => {
    expect(text()).toContain('Loading financial dashboard');
    expect(text()).not.toContain('Total Balance');
    expect(fixture.nativeElement.querySelectorAll('.summary-card')).toHaveLength(0);
    finish(emptyDashboard(period));
  });
  it('renders four real summaries, decimals, negative savings and PHP formatting', () => {
    finish(populatedDashboard(period));
    expect(fixture.nativeElement.querySelectorAll('.summary-card')).toHaveLength(4);
    expect(text()).toContain('Monthly Income');
    expect(text()).toContain('Monthly Expenses');
    expect(text()).toContain('Net Savings');
    expect(text()).toContain('Savings Rate');
    expect(text()).toContain('1,000.10');
    expect(text()).toContain('-\u20b1200.25');
    expect(text()).toContain('-20.02%');
    expect(text()).toContain('October 2026');
    expect(text()).not.toContain('Sample data');
    expect(page.store.data()?.summary.totalIncome).toBe(1000.1);
  });
  it('renders successful empty data with an unavailable savings rate', () => {
    finish(emptyDashboard(period));
    expect(text()).toContain('Unavailable');
    expect(text()).toContain('No transactions recorded');
    expect(text()).toContain('No expenses recorded');
    expect(text()).toContain('No budgets set');
    expect(text()).not.toContain('0.00%');
    expect(page.donutBackground()).toBe('var(--track-bg)');
    expect(page.chartMax()).toBeGreaterThan(0);
  });
  it('keeps rate unavailable with zero income and actual expenses', () => {
    const data = populatedDashboard(period);
    data.summary = {
      totalIncome: 0,
      totalExpenses: 1200.35,
      netSavings: -1200.35,
      savingsRate: null,
    };
    finish(data);
    expect(text()).toContain('Unavailable');
    expect(text()).toContain('-\u20b11,200.35');
  });
  it('maps category IDs and displays expense amounts as negative without mutating the DTO', () => {
    finish(populatedDashboard(period), [
      { id: 1, name: 'Dining', type: 'EXPENSE', color: null, createdAt: '', updatedAt: '' },
    ]);
    expect(page.transactions()[0].category).toBe('Dining');
    expect(page.transactions()[0].signedAmount).toBe(-1200.35);
    expect(page.store.data()?.recentTransactions[0].amount).toBe(1200.35);
    expect(text()).toContain('Dining');
    expect(text()).toContain('Oct 1, 2026');
  });
  it('uses breakdown labels while lookup is unavailable and never invents category names', () => {
    const data = populatedDashboard(period);
    data.budgetUtilization[0].categoryId = 999;
    http.expectOne('/api/v1/categories').flush({}, { status: 500, statusText: 'Error' });
    for (const request of http.match((request) => request.url === '/api/v1/dashboard')) {
      request.flush(
        request.request.params.get('month') === '10'
          ? data
          : emptyDashboard({
              month: Number(request.request.params.get('month')),
              year: Number(request.request.params.get('year')),
            }),
      );
    }
    fixture.detectChanges();
    expect(page.transactions()[0].category).toBe('Food');
    expect(page.budgets()[0].label).toBe('Category #999 (name unavailable)');
    expect(text()).toContain('Retry category lookup');
  });
  it('generates dynamic chart values, colors and accessible descriptions', () => {
    finish(populatedDashboard(period));
    expect(page.expenses()[0].percent).toBe(100);
    expect(page.donutBackground()).toBe('conic-gradient(#14b8a6 0% 100%)');
    expect(fixture.nativeElement.querySelector('.donut').getAttribute('aria-label')).toBe(
      'Food: 100%',
    );
    expect(page.chartDescription()).toContain(
      'October 2026: income PHP 1000.1, expenses PHP 1200.35',
    );
    expect(page.chartMax()).toBe(2000);
    expect(fixture.nativeElement.querySelectorAll('.income-bar')).toHaveLength(6);
  });
  it('retains overspending values while clamping visual and ARIA progress to 100', () => {
    finish(populatedDashboard(period));
    const bar = fixture.nativeElement.querySelector('[role="progressbar"]');
    expect(page.budgets()[0].percentageUsed).toBe(120.04);
    expect(page.budgets()[0].remainingAmount).toBe(-200.35);
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
    expect(bar.getAttribute('aria-valuetext')).toContain('120.04%');
    expect(fixture.nativeElement.querySelector('.budget-fill').style.width).toBe('100%');
    expect(text()).toContain('120.04%');
    expect(text()).toContain('EXCEEDED');
    expect(text()).toContain('-\u20b1200.35');
  });
  it('preserves all three backend budget status labels', () => {
    const data = populatedDashboard(period);
    const budget = data.budgetUtilization[0];
    data.budgetUtilization = ['SAFE', 'WARNING', 'EXCEEDED'].map((status, index) => ({
      ...budget,
      id: index + 1,
      budgetStatus: status as typeof budget.budgetStatus,
    }));
    finish(data);
    for (const status of ['SAFE', 'WARNING', 'EXCEEDED'])
      expect(fixture.nativeElement.querySelector(`.budget-fill.${status}`)).toBeTruthy();
  });
  it('shows an API error and retry action rather than fallback cards', () => {
    http
      .expectOne('/api/v1/dashboard?month=10&year=2026')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/v1/categories').flush([]);
    for (const request of http.match((request) => request.url === '/api/v1/dashboard'))
      request.flush(
        emptyDashboard({
          month: Number(request.request.params.get('month')),
          year: Number(request.request.params.get('year')),
        }),
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'could not be loaded',
    );
    expect(text()).toContain('Retry');
    expect(fixture.nativeElement.querySelectorAll('.summary-card')).toHaveLength(0);
  });
  it('validates period controls without sending invalid requests', () => {
    finish(emptyDashboard(period));
    page.controls.controls.year.setValue(1999);
    TestBed.tick();
    fixture.detectChanges();
    http.expectNone((request) => request.url === '/api/v1/dashboard');
    expect(text()).toContain('Choose a year from 2000 to 2100');
    page.controls.controls.year.setValue(2026);
    page.controls.controls.month.setValue(12);
    TestBed.tick();
    for (const request of http.match(() => true))
      request.flush(
        request.request.url === '/api/v1/categories'
          ? []
          : emptyDashboard({
              month: Number(request.request.params.get('month')),
              year: Number(request.request.params.get('year')),
            }),
      );
    expect(page.store.data()?.month).toBe(12);
  });
});
