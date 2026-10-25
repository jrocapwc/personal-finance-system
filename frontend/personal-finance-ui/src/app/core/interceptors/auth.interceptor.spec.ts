import { DOCUMENT } from '@angular/common';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth';
import { testCredentials, testLogin } from '../auth.testing';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: AuthService;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.login(testCredentials).subscribe();
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(testLogin);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  it.each(['/api/v1/transactions', '/api/v1/budgets?month=1'])(
    'attaches bearer to own API: %s',
    (url) => {
      client.get(url).subscribe();
      const request = http.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe('Bearer test-token-only');
      request.flush({});
    },
  );
  it('allows same-origin absolute API URLs', () => {
    const url = new URL('/api/v1/transactions', TestBed.inject(DOCUMENT).baseURI).href;
    client.get(url).subscribe();
    const request = http.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush({});
  });
  it.each([
    'https://third-party.example/api/v1/transactions',
    '//third-party.example/api/v1/transactions',
    '/api/v1/auth/login',
    '/api/v1/auth/register?source=test',
    '/assets/logo.svg',
    '/api/v10/users',
    '/api/v1/../../external',
  ])('does not leak tokens to %s', (url) => {
    client.get(url).subscribe();
    const request = http.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
  it('omits tokens when missing', () => {
    auth.logout();
    client.get('/api/v1/transactions').subscribe();
    const request = http.expectOne('/api/v1/transactions');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
  it('omits expired tokens and clears the user', () => {
    vi.useFakeTimers();
    vi.setSystemTime(Date.now() + 3601000);
    client.get('/api/v1/transactions').subscribe();
    const request = http.expectOne('/api/v1/transactions');
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(auth.currentUser()).toBeNull();
    request.flush({});
  });
  it('clears rejected sessions on API 401', () => {
    client.get('/api/v1/transactions').subscribe({ error: () => {} });
    http.expectOne('/api/v1/transactions').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
  });
  it('retains sessions on a forbidden response', () => {
    client.get('/api/v1/transactions').subscribe({ error: () => {} });
    http.expectOne('/api/v1/transactions').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.isAuthenticated()).toBe(true);
  });
});
