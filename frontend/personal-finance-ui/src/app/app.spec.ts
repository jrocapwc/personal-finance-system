import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { of } from 'rxjs';
import { DashboardService } from './core/services/dashboard';
import { CategoryService } from './core/services/category';
import { emptyDashboard } from './features/dashboard/dashboard.testing';
import { ReportingPeriod } from './core/models/dashboard.model';
import { routes } from './app.routes';
import { AuthService } from './core/services/auth';
import { ThemeService } from './core/services/theme';
import { testCredentials, testLogin } from './core/auth.testing';

describe('Authentication routes and layouts', () => {
  beforeEach(() => {
    localStorage.removeItem('finance-theme');
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DashboardService,
          useValue: { getMonthly: (period: ReportingPeriod) => of(emptyDashboard(period)) },
        },
        { provide: CategoryService, useValue: { list: () => of([]) } },
      ],
    });
    TestBed.inject(AuthService).restoreSession().subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/session').flush({}, {status: 401, statusText: 'Unauthorized'});
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.removeItem('finance-theme');
  });
  function login() {
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/csrf').flush(null);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush(testLogin);
  }
  it('creates the root and applies light/dark theme to the entire outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.dark-theme')).toBeNull();
    TestBed.inject(ThemeService).toggleTheme();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.dark-theme')).toBeTruthy();
    expect(localStorage.getItem('finance-theme')).toBe('dark');
  });
  it('keeps the live dashboard under the shared light/dark theme wrapper', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    login();
    await TestBed.inject(Router).navigateByUrl('/dashboard');
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('.app-shell app-dashboard .summary-grid'),
    ).toBeTruthy();
    TestBed.inject(ThemeService).toggleTheme();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('.dark-theme app-dashboard .summary-grid'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-ai-assistant')).toBeTruthy();
  });
  it('restores the existing theme preference after rendering', async () => {
    localStorage.setItem('finance-theme', 'dark');
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(TestBed.inject(ThemeService).isDarkMode()).toBe(true);
  });
  it('opens Account Settings in the shared layout, updates the header name and supports both themes', async () => {
    login();
    const harness = await RouterTestingHarness.create('/account-settings');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/users/me').flush(testLogin.user);
    harness.detectChanges();
    const element = harness.routeNativeElement!;
    expect(element.querySelector('app-account-settings')).toBeTruthy();
    expect(element.querySelector('a[routerLink="/account-settings"]')?.textContent).toContain('Account Settings');
    expect(element.querySelector('.toolbar-title')?.textContent).toContain('Account Settings');
    expect(element.querySelector('app-ai-assistant')).toBeTruthy();
    const edit = Array.from(element.querySelectorAll('button')).find((button) => button.textContent?.includes('Edit Profile'))!;
    edit.click(); harness.detectChanges();
    const input = element.querySelector<HTMLInputElement>('#account-name')!;
    input.value = 'Renamed User'; input.dispatchEvent(new Event('input'));
    element.querySelector('app-account-settings form')!.dispatchEvent(new Event('submit'));
    TestBed.inject(HttpTestingController).expectOne('/api/v1/users/me').flush({ ...testLogin.user, name: 'Renamed User' });
    harness.detectChanges();
    expect(element.querySelector('.profile')?.textContent).toContain('Renamed User');
    expect(element.querySelector('app-account-settings')?.textContent).toContain('Renamed User');
    TestBed.inject(ThemeService).toggleTheme(); harness.detectChanges();
    expect(TestBed.inject(ThemeService).isDarkMode()).toBe(true);
    expect(element.querySelector('app-account-settings')).toBeTruthy();
  });
  it.each(['/dashboard', '/categories', '/transactions', '/budgets', '/account-settings', '/', '/unknown'])(
    'redirects unauthenticated %s to login',
    async (url) => {
      const harness = await RouterTestingHarness.create(url);
      expect(TestBed.inject(Router).url.split('?')[0]).toBe('/login');
      expect(harness.routeNativeElement?.querySelector('app-login')).toBeTruthy();
      expect(harness.routeNativeElement?.querySelector('.sidebar')).toBeNull();
      expect(harness.routeNativeElement?.querySelector('app-ai-assistant')).toBeNull();
    },
  );
  it('register is public and has no dashboard layout', async () => {
    const harness = await RouterTestingHarness.create('/register');
    expect(harness.routeNativeElement?.querySelector('app-register')).toBeTruthy();
    expect(harness.routeNativeElement?.querySelector('mat-toolbar')).toBeNull();
  });
  it('shows registration success on login', async () => {
    const harness = await RouterTestingHarness.create('/login?registered=true');
    expect(harness.routeNativeElement?.querySelector('[role="status"]')?.textContent).toContain(
      'Account created successfully',
    );
  });
  it('opens dashboard, preserves charts and assistant modes, and logs out', async () => {
    login();
    const harness = await RouterTestingHarness.create('/dashboard');
    const element = harness.routeNativeElement!;
    expect(element.querySelector('app-dashboard')).toBeTruthy();
    expect(element.textContent).toContain('Test User');
    expect(element.textContent).toContain('Income');
    expect(element.textContent).toContain('Recent Transactions');
    const toggle = element.querySelector<HTMLButtonElement>(
      'button[aria-label="Ask Yaman Ai"]',
    )!;
    toggle.click();
    harness.detectChanges();
    expect(element.querySelector('[aria-label="Ask Yaman Ai"]')).toBeTruthy();
    const general = Array.from(
      element.querySelectorAll<HTMLButtonElement>('.mode-selector button'),
    )[0];
    general.click();
    harness.detectChanges();
    expect(general.getAttribute('aria-pressed')).toBe('true');
    const themeToggle = element.querySelector<HTMLButtonElement>(
      '[aria-label="Switch to dark mode"]',
    )!;
    themeToggle.click();
    harness.detectChanges();
    expect(TestBed.inject(ThemeService).isDarkMode()).toBe(true);
    const logout = Array.from(element.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Logout'),
    )!;
    logout.click();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/csrf').flush(null);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/logout').flush(null);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url.split('?')[0]).toBe('/login');
    await harness.navigateByUrl('/dashboard');
    expect(TestBed.inject(Router).url.split('?')[0]).toBe('/login');
  });
  it.each(['/login', '/register'])(
    'redirects authenticated users from %s to dashboard',
    async (url) => {
      login();
      await RouterTestingHarness.create(url);
      expect(TestBed.inject(Router).url).toBe('/dashboard');
    },
  );
});
