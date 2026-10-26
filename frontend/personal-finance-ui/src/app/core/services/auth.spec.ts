import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth';
import { testCredentials, testLogin, testUser } from '../auth.testing';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  function login(expiresIn = 3600) {
    auth.login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual(testCredentials);
    request.flush({ ...testLogin, expiresIn });
  }
  it('starts without a session', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.currentUser()).toBeNull();
  });
  it('stores only the safe reactive user in memory', () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    login();
    expect('getToken' in auth).toBe(false);
    expect(auth.currentUser()).toEqual(testUser);
    expect(auth.isAuthenticated()).toBe(true);
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
  it('sends only registration contract fields and does not authenticate', () => {
    auth
      .register({ name: testUser.name, ...testCredentials, confirmPassword: 'ignored' } as never)
      .subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({ name: testUser.name, ...testCredentials });
    request.flush(testUser, { status: 201, statusText: 'Created' });
    expect(auth.isAuthenticated()).toBe(false);
  });
  it('leaves no session after a failed login', () => {
    auth.login(testCredentials).subscribe({ error: () => {} });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.currentUser()).toBeNull();
  });
  it('expires the session without requiring another request', () => {
    vi.useFakeTimers();
    login(2);
    vi.advanceTimersByTime(2000);
    expect(auth.currentUser()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/dashboard' } });
  });
  it('checks wall clock expiration after a suspended browser resumes', () => {
    vi.useFakeTimers();
    login(2);
    vi.setSystemTime(Date.now() + 3000);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.currentUser()).toBeNull();
  });
  it('logout clears state and redirects to login', () => {
    login();
    auth.logout();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.currentUser()).toBeNull();
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/login');
  });
  it('waits for an in-flight login before clearing its cookie', () => {
    auth.login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    auth.logout();
    http.expectNone('/api/v1/auth/logout');
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    expect(auth.isAuthenticated()).toBe(false);
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
    expect(auth.isAuthenticated()).toBe(false);
  });
  it('does not restore a profile response received after session invalidation', () => {
    login();
    auth.updateProfile('Changed Name').subscribe();
    const update = http.expectOne('/api/v1/users/me');
    auth.invalidate();
    update.flush({ ...testUser, name: 'Changed Name' });
    expect(auth.currentUser()).toBeNull();
  });
  it('does not accept another user identity from an account response', () => {
    login();
    auth.loadProfile().subscribe();
    http.expectOne('/api/v1/users/me').flush({ ...testUser, id: 'another-user', name: 'Other User' });
    expect(auth.currentUser()).toEqual(testUser);
  });
  it('preserves Account Settings as a safe destination after sign-in', () => {
    expect(auth.safeReturnUrl('/account-settings')).toBe('/account-settings');
    expect(auth.safeReturnUrl('/account-settings/../../login')).toBe('/dashboard');
  });
  it.each([0, -1, NaN])('rejects an invalid lifetime %s', (expiresIn) => {
    let failed = false;
    auth.login(testCredentials).subscribe({ error: () => (failed = true) });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush({ ...testLogin, expiresIn });
    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });
});
