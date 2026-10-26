import { DOCUMENT } from '@angular/common';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService, SessionStatus } from './auth';
import { authGuard } from '../guards/auth.guard';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { testCredentials, testLogin, testUser } from '../auth.testing';

describe('Cookie session initialization and XSRF', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    http.verify({ ignoreCancelled: true });
  });
  function guard(url = '/transactions?search=rent') {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url } as never),
    ) as Observable<boolean | UrlTree>;
  }
  it('shares restoration and holds direct navigation until the server confirms the profile', () => {
    let allowed: boolean | UrlTree | undefined;
    const states: SessionStatus[] = [];
    guard().subscribe((value) => (allowed = value));
    auth.restoreSession().subscribe((value) => states.push(value));
    expect(auth.status()).toBe('initializing');
    expect(auth.currentUser()).toBeNull();
    expect(allowed).toBeUndefined();
    const request = http.expectOne('/api/v1/auth/session');
    expect(request.request.transferCache).toBe(false);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(testLogin);
    expect(allowed).toBe(true);
    expect(states).toEqual(['authenticated']);
    expect(auth.currentUser()).toEqual(testUser);
  });
  it('redirects an expired or missing session and preserves its intended route', () => {
    let destination = '';
    guard().subscribe((value) => (destination = value.toString()));
    http.expectOne('/api/v1/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.status()).toBe('unauthenticated');
    expect(destination).toContain('/login?returnUrl=');
    expect(decodeURIComponent(destination)).toContain('/transactions?search=rent');
  });
  it.each([0, 503])(
    'keeps status recoverable after session-check failure %s and retries',
    (status) => {
      let destination = '';
      guard().subscribe((value) => (destination = value.toString()));
      const request = http.expectOne('/api/v1/auth/session');
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({}, { status, statusText: 'Unavailable' });
      expect(auth.status()).toBe('error');
      expect(destination).toContain('/session-error');
      auth.restoreSession(true).subscribe();
      http.expectOne('/api/v1/auth/session').flush(testLogin);
      expect(auth.isAuthenticated()).toBe(true);
    },
  );
  it('ignores a session response received after logout', () => {
    auth.restoreSession().subscribe();
    const session = http.expectOne('/api/v1/auth/session');
    auth.logout();
    session.flush(testLogin);
    expect(auth.currentUser()).toBeNull();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
  });
  it('bootstraps before login and uses the rotated readable token for mutation and logout', () => {
    const document = TestBed.inject(DOCUMENT);
    auth.login(testCredentials).subscribe();
    http.expectNone('/api/v1/auth/login');
    document.cookie = 'XSRF-TOKEN=before-login; Path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.headers.get('X-XSRF-TOKEN')).toBe('before-login');
    document.cookie = 'XSRF-TOKEN=after-login; Path=/';
    login.flush(testLogin);
    TestBed.inject(HttpClient).post('/api/v1/categories', {}).subscribe();
    const mutation = http.expectOne('/api/v1/categories');
    expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('after-login');
    mutation.flush({});
    auth.logout();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.headers.get('X-XSRF-TOKEN')).toBe('after-login');
    logout.flush(null);
  });
  it('does not send XSRF or opt into credentials for third-party requests', () => {
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=private-csrf; Path=/';
    for (const url of [
      'https://external.example/api/v1/categories',
      '//external.example/api/v1/categories',
    ]) {
      TestBed.inject(HttpClient).post(url, {}).subscribe();
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      expect(request.request.withCredentials).toBe(false);
      request.flush({});
    }
  });
  it('bootstraps a missing CSRF cookie before dispatching a private mutation', () => {
    const document = TestBed.inject(DOCUMENT);
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    TestBed.inject(HttpClient).post('/api/v1/categories', {}).subscribe();
    http.expectNone('/api/v1/categories');
    document.cookie = 'XSRF-TOKEN=recovered; Path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const mutation = http.expectOne('/api/v1/categories');
    expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('recovered');
    mutation.flush({});
  });
  it('shares one CSRF bootstrap between simultaneous private mutations', () => {
    const document = TestBed.inject(DOCUMENT);
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    const client = TestBed.inject(HttpClient);
    client.post('/api/v1/categories', {}).subscribe();
    client.post('/api/v1/budgets', {}).subscribe();
    const bootstrap = http.expectOne('/api/v1/auth/csrf');
    http.expectNone('/api/v1/categories');
    document.cookie = 'XSRF-TOKEN=shared-bootstrap; Path=/';
    bootstrap.flush(null);
    for (const url of ['/api/v1/categories', '/api/v1/budgets']) {
      const mutation = http.expectOne(url);
      expect(mutation.request.headers.get('X-XSRF-TOKEN')).toBe('shared-bootstrap');
      mutation.flush({});
    }
  });
  it('exposes a failed server logout and allows it to be retried', () => {
    auth.restoreSession().subscribe();
    http.expectOne('/api/v1/auth/session').flush(testLogin);
    auth.logout();
    expect(auth.currentUser()).toBeNull();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    expect(auth.status()).toBe('logout-error');
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenLastCalledWith('/session-error');
    auth.logout();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
    expect(auth.status()).toBe('unauthenticated');
  });
  it.each([
    '//evil.example',
    'https://evil.example',
    '/login',
    '/session-error',
    '/dashboard/../../login',
  ])('rejects unsafe return destination %s', (url) => {
    expect(auth.safeReturnUrl(url)).toBe('/dashboard');
  });
});

describe('Server rendering session safety', () => {
  it('makes no session HTTP requests and never transfers a user into public HTML', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    const auth = TestBed.inject(AuthService);
    let status: SessionStatus | undefined;
    auth.restoreSession().subscribe((value) => (status = value));
    expect(status).toBe('unauthenticated');
    expect(auth.currentUser()).toBeNull();
    TestBed.inject(HttpTestingController).expectNone('/api/v1/auth/session');
  });
});
