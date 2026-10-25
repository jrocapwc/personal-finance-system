import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Login } from './login/login';
import { Register } from './register/register';
import { AuthService } from '../../core/services/auth';
import { testCredentials, testLogin, testUser } from '../../core/auth.testing';

describe('Authentication forms', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Login, Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => http.verify());
  it.each([Login, Register])('renders an accessible form for %s', (component) => {
    const fixture = TestBed.createComponent(component as typeof Login);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('label[for="email"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#password').type).toBe('password');
    fixture.nativeElement.querySelector('button[aria-label="Show password"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#password').type).toBe('text');
  });
  it('validates required login fields and email', () => {
    const form = TestBed.createComponent(Login).componentInstance;
    form.submit();
    expect(form.form.invalid).toBe(true);
    form.form.setValue({ email: 'invalid', password: 'test' });
    expect(form.form.invalid).toBe(true);
    http.expectNone('/api/v1/auth/login');
  });
  it('logs in and navigates to dashboard', () => {
    const page = TestBed.createComponent(Login).componentInstance;
    page.form.setValue(testCredentials);
    page.submit();
    expect(page.loading()).toBe(true);
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    expect(TestBed.inject(AuthService).currentUser()).toEqual(testUser);
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(page.loading()).toBe(false);
    expect(page.form.controls.password.value).toBe('');
  });
  it('renders invalid credentials without exposing server details', () => {
    const fixture = TestBed.createComponent(Login);
    const page = fixture.componentInstance;
    page.form.setValue(testCredentials);
    page.submit();
    http
      .expectOne('/api/v1/auth/login')
      .flush({ message: 'sensitive detail' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Incorrect email or password',
    );
    expect(fixture.nativeElement.textContent).not.toContain('sensitive detail');
    expect(page.loading()).toBe(false);
  });
  function registration() {
    const page = TestBed.createComponent(Register).componentInstance;
    page.form.setValue({
      name: testUser.name,
      ...testCredentials,
      confirmPassword: testCredentials.password,
    });
    return page;
  }
  it('validates name, email and password boundaries', () => {
    const page = registration();
    for (const name of ['', '   ', 'a'.repeat(101)]) {
      page.form.controls.name.setValue(name);
      expect(page.form.controls.name.invalid).toBe(true);
    }
    for (const email of ['', 'invalid', 'a'.repeat(250) + '@x.com']) {
      page.form.controls.email.setValue(email);
      expect(page.form.controls.email.invalid).toBe(true);
    }
    for (const password of ['', 'short', ' '.repeat(12), 'a'.repeat(73)]) {
      page.form.controls.password.setValue(password);
      expect(page.form.controls.password.invalid).toBe(true);
    }
    page.form.controls.password.setValue('a'.repeat(12));
    expect(page.form.controls.password.valid).toBe(true);
  });
  it('requires confirmation and revalidates when the original password changes', () => {
    const page = registration();
    expect(page.form.valid).toBe(true);
    page.form.controls.password.setValue('AnotherTest123!');
    expect(page.form.hasError('passwordMismatch')).toBe(true);
    page.form.controls.confirmPassword.setValue('AnotherTest123!');
    expect(page.form.valid).toBe(true);
    page.form.controls.confirmPassword.setValue('');
    expect(page.form.invalid).toBe(true);
  });
  it('enforces UTF-8 bytes with multibyte and supplementary characters', () => {
    const page = registration();
    for (const value of ['a'.repeat(72), '\u00e9'.repeat(36), '\u{1f600}'.repeat(18)]) {
      page.form.controls.password.setValue(value);
      expect(page.form.controls.password.valid).toBe(true);
      page.form.controls.password.setValue(value + 'a');
      expect(page.form.controls.password.hasError('utf8Bytes')).toBe(true);
    }
  });
  it('sends only contract fields and redirects registration to login with a notice', () => {
    const page = registration();
    page.submit();
    page.submit();
    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({ name: testUser.name, ...testCredentials });
    request.flush(testUser, { status: 201, statusText: 'Created' });
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { registered: 'true' },
    });
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(page.loading()).toBe(false);
  });
  it('renders a helpful duplicate email error', () => {
    const page = registration();
    page.submit();
    http.expectOne('/api/v1/auth/register').flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toContain('already exists');
    expect(page.loading()).toBe(false);
  });
  it('handles network failures and allows retry', () => {
    const page = registration();
    page.submit();
    http.expectOne('/api/v1/auth/register').error(new ProgressEvent('error'));
    expect(page.error()).toContain('unavailable');
    expect(page.loading()).toBe(false);
    page.submit();
    http.expectOne('/api/v1/auth/register').flush(testUser);
  });
});
