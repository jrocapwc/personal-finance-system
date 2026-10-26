import { DOCUMENT } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { testLogin, testUser } from '../../core/auth.testing';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { AuthService } from '../../core/services/auth';
import { AccountSettings } from './account-settings';

describe('Account Settings', () => {
  let fixture: ComponentFixture<AccountSettings>, page: AccountSettings;
  let http: HttpTestingController, auth: AuthService;
  const profileUrl = '/api/v1/users/me';
  const passwordUrl = profileUrl + '/password';
  const nextPassword = 'NewSecurePassword456';
  const currentPassword = 'CurrentPassword123';
  const snack = { open: vi.fn() };

  beforeEach(() => {
    snack.open.mockClear();
    TestBed.configureTestingModule({
      imports: [AccountSettings],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snack },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=account-csrf; Path=/';
    auth.restoreSession().subscribe();
    http.expectOne('/api/v1/auth/session').flush(testLogin);
    fixture = TestBed.createComponent(AccountSettings);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => {
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    http.verify({ ignoreCancelled: true });
  });
  function loaded() {
    const request = http.expectOne(profileUrl);
    expect(request.request.method).toBe('GET');
    expect(request.request.transferCache).toBe(false);
    request.flush(testUser);
    fixture.detectChanges();
  }
  function password() {
    page.passwordForm.setValue({
      currentPassword,
      newPassword: nextPassword,
      confirmPassword: nextPassword,
    });
  }

  it('loads the real profile and displays name, email, and creation date', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading your profile');
    loaded();
    expect(fixture.nativeElement.textContent).toContain(testUser.name);
    expect(fixture.nativeElement.textContent).toContain(testUser.email);
    expect(fixture.nativeElement.textContent).toContain('January 1, 2026');
    expect(fixture.nativeElement.textContent).not.toContain('passwordHash');
  });

  it('offers retry after a profile loading failure', () => {
    http.expectOne(profileUrl).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(page.loadError()).toContain('connect');
    const retry = fixture.nativeElement.querySelector('button');
    expect(retry.textContent).toContain('Retry');
    retry.click();
    loaded();
    expect(page.loadError()).toBe('');
  });

  it.each(['', '   ', 'a'.repeat(101)])('rejects invalid profile name %s', (name) => {
    loaded();
    page.edit();
    page.profileForm.controls.name.setValue(name);
    page.save();
    expect(page.profileForm.invalid).toBe(true);
    http.expectNone(profileUrl);
  });

  it('saves only the trimmed name, prevents duplicates, and updates shared state without replacing the session', () => {
    loaded();
    page.edit();
    const revision = auth.sessionRevision;
    page.profileForm.controls.name.setValue('  Updated Full Name  ');
    page.save();
    page.save();
    const request = http.expectOne(profileUrl);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Updated Full Name' });
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('account-csrf');
    expect(request.request.headers.has('Authorization')).toBe(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    request.flush({ ...testUser, name: 'Updated Full Name' });
    expect(auth.currentUser()?.name).toBe('Updated Full Name');
    expect(auth.currentUser()?.email).toBe(testUser.email);
    expect(auth.sessionRevision).toBe(revision);
    expect(page.success()).toContain('updated');
    expect(page.editing()).toBe(false);
  });

  it('cancels editing without an API call', () => {
    loaded();
    page.edit();
    page.profileForm.controls.name.setValue('Discard me');
    page.cancel();
    expect(page.profileForm.controls.name.value).toBe(testUser.name);
    expect(page.editing()).toBe(false);
    http.expectNone(profileUrl);
  });

  it('requires all password fields and displays inline errors', () => {
    loaded();
    page.changePassword();
    fixture.detectChanges();
    expect(page.passwordForm.invalid).toBe(true);
    for (const field of page.passwordFields)
      expect(page.fieldError(field.key)).toContain('required');
    http.expectNone(passwordUrl);
  });

  it.each(['short', ' '.repeat(12), 'a'.repeat(73), '界'.repeat(25), '😀'.repeat(19)])(
    'rejects invalid new password boundary case %#',
    (value) => {
      loaded();
      password();
      page.passwordForm.patchValue({ newPassword: value, confirmPassword: value });
      page.changePassword();
      expect(page.passwordForm.invalid).toBe(true);
      expect(page.fieldError('newPassword')).not.toBe('');
      http.expectNone(passwordUrl);
    },
  );

  it.each(['a'.repeat(12), 'a'.repeat(72), '界'.repeat(24), '😀'.repeat(18)])(
    'accepts valid character and UTF-8 byte boundaries %#',
    (value) => {
      loaded();
      password();
      page.passwordForm.patchValue({ newPassword: value, confirmPassword: value });
      expect(page.passwordForm.valid).toBe(true);
    },
  );

  it('rejects password reuse and mismatching confirmation, revalidating after changes', () => {
    loaded();
    password();
    page.passwordForm.patchValue({
      newPassword: currentPassword,
      confirmPassword: currentPassword,
    });
    page.changePassword();
    expect(page.fieldError('newPassword')).toContain('different');
    page.passwordForm.controls.newPassword.setValue(nextPassword);
    expect(page.fieldError('confirmPassword')).toContain('do not match');
    page.passwordForm.controls.confirmPassword.setValue(nextPassword);
    expect(page.passwordForm.valid).toBe(true);
  });

  it('toggles each password independently with accessible buttons', () => {
    loaded();
    for (const field of page.passwordFields) {
      const input = fixture.nativeElement.querySelector('#' + field.key);
      expect(input.type).toBe('password');
      const button = fixture.nativeElement.querySelector(
        `button[aria-label="Show ${field.label.toLowerCase()}"]`,
      );
      button.click();
      fixture.detectChanges();
      expect(input.type).toBe('text');
      expect(button.getAttribute('aria-pressed')).toBe('true');
      button.click();
      fixture.detectChanges();
      expect(input.type).toBe('password');
    }
  });

  it('sends only the password contract, blocks duplicates, clears secrets and signs out on 204', () => {
    loaded();
    password();
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const changes = vi.fn();
    auth.sessionChanges.subscribe(changes);
    page.changePassword();
    page.changePassword();
    const request = http.expectOne(passwordUrl);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ currentPassword, newPassword: nextPassword });
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('account-csrf');
    expect(page.changingPassword()).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.passwordForm.getRawValue()).toEqual({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    expect(page.changingPassword()).toBe(false);
    expect(auth.currentUser()).toBeNull();
    expect(auth.status()).toBe('unauthenticated');
    expect(changes).toHaveBeenCalled();
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/login');
    expect(snack.open).toHaveBeenCalledWith(
      expect.stringContaining('Please sign in again'),
      'Dismiss',
      expect.anything(),
    );
    expect(storage).not.toHaveBeenCalled();
    http.expectNone('/api/v1/auth/logout');
    storage.mockRestore();
  });

  it('safely displays an incorrect-current-password error without rendering server details', () => {
    loaded();
    password();
    page.changePassword();
    http
      .expectOne(passwordUrl)
      .flush(
        { code: 'INVALID_PASSWORD_CHANGE', message: 'sensitive-value' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(page.passwordError()).toContain('Check your current password');
    expect(fixture.nativeElement.textContent).not.toContain('sensitive-value');
    expect(auth.isAuthenticated()).toBe(true);
  });

  it.each([400, 403, 404, 409, 500, 0])(
    'handles API error %s without automatic password retries',
    (status) => {
      loaded();
      password();
      page.changePassword();
      const request = http.expectOne(passwordUrl);
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({ message: 'sensitive-value' }, { status, statusText: 'Failure' });
      fixture.detectChanges();
      expect(page.passwordError()).not.toBe('');
      expect(page.passwordError()).not.toContain('sensitive-value');
      expect(page.changingPassword()).toBe(false);
      expect(auth.isAuthenticated()).toBe(true);
      http.expectNone(passwordUrl);
      if (status === 403) expect(page.passwordError()).toContain('Refresh the page');
    },
  );

  it('invalidates expired sessions and immediately clears passwords', () => {
    loaded();
    password();
    page.changePassword();
    http.expectOne(passwordUrl).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.currentUser()).toBeNull();
    expect(page.passwordForm.controls.currentPassword.value).toBe('');
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('bootstraps missing CSRF before dispatching without a Bearer token', () => {
    loaded();
    password();
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    page.changePassword();
    http.expectNone(passwordUrl);
    TestBed.inject(DOCUMENT).cookie = 'XSRF-TOKEN=restored-csrf; Path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne(passwordUrl);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('restored-csrf');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null);
  });

  it('clears sensitive forms on destruction', () => {
    loaded();
    password();
    fixture.destroy();
    expect(page.passwordForm.controls.currentPassword.value).toBe('');
    expect(page.passwordForm.controls.newPassword.value).toBe('');
  });
});
