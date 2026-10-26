import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  Subject,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '../models/auth.model';

export type SessionStatus =
  'initializing' | 'authenticated' | 'unauthenticated' | 'error' | 'logout-error';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private expiresAt = 0;
  private revision = 0;
  private restoration?: Observable<SessionStatus>;
  private csrfBootstrap?: Observable<void>;
  private pendingLogin?: Observable<LoginResponse>;
  private loggingOut = false;
  private channel?: BroadcastChannel;
  private readonly userState = signal<UserResponse | null>(null);
  readonly currentUser = this.userState.asReadonly();
  readonly status = signal<SessionStatus>('initializing');
  readonly logoutPending = signal(false);
  private readonly sessionChangesSubject = new Subject<void>();
  readonly sessionChanges = this.sessionChangesSubject.asObservable();
  get sessionRevision(): number {
    return this.revision;
  }

  constructor() {
    if (this.browser && typeof window.BroadcastChannel === 'function') {
      this.channel = new window.BroadcastChannel('finance-session');
      this.channel.onmessage = () => this.invalidate();
    }
    inject(DestroyRef).onDestroy(() => {
      this.clearSession();
      this.channel?.close();
    });
  }
  ensureCsrf(): Observable<void> {
    if (this.csrfBootstrap) return this.csrfBootstrap;
    this.csrfBootstrap = this.http.get<void>('/api/v1/auth/csrf', { transferCache: false }).pipe(
      finalize(() => {
        this.csrfBootstrap = undefined;
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    return this.csrfBootstrap;
  }
  register({ name, email, password }: RegisterRequest): Observable<UserResponse> {
    return this.ensureCsrf().pipe(
      switchMap(() =>
        this.http.post<UserResponse>('/api/v1/auth/register', { name, email, password }),
      ),
    );
  }
  login({ email, password }: LoginRequest): Observable<LoginResponse> {
    if (this.loggingOut) return throwError(() => new Error('Logout is still pending'));
    this.clearSession();
    this.status.set('unauthenticated');
    const revision = this.revision;
    const result = this.ensureCsrf().pipe(
      switchMap(() => this.http.post<LoginResponse>('/api/v1/auth/login', { email, password })),
      tap((response) => {
        if (revision !== this.revision) return;
        this.accept(response);
        this.channel?.postMessage('changed');
      }),
      finalize(() => {
        if (this.pendingLogin === result) this.pendingLogin = undefined;
      }),
      // Logout waits for submitted login to settle before clearing its cookie.
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.pendingLogin = result;
    return result;
  }
  restoreSession(force = false): Observable<SessionStatus> {
    if (!this.browser) return of('unauthenticated');
    if (this.restoration) return this.restoration;
    if (!force && this.status() !== 'initializing') return of(this.status());
    const revision = this.revision;
    this.status.set('initializing');
    const request = this.http
      .get<LoginResponse>('/api/v1/auth/session', { transferCache: false })
      .pipe(
        tap((response) => {
          if (revision === this.revision) this.accept(response);
        }),
        map(() => this.status()),
        catchError((error: unknown) => {
          if (revision === this.revision) {
            if (error instanceof HttpErrorResponse && error.status === 401) {
              this.clearSession();
              this.status.set('unauthenticated');
            } else this.status.set('error');
          }
          return of(this.status());
        }),
        finalize(() => {
          this.restoration = undefined;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    this.restoration = request;
    return request;
  }
  isAuthenticated(): boolean {
    if (this.userState() && Date.now() >= this.expiresAt) this.invalidate();
    return this.status() === 'authenticated' && this.userState() !== null;
  }
  invalidate(): void {
    const returnUrl = this.router.url;
    this.clearSession();
    this.status.set('unauthenticated');
    void this.router.navigate(['/login'], {
      queryParams: { returnUrl: this.safeReturnUrl(returnUrl) },
    });
  }
  logout(): void {
    if (this.loggingOut) return;
    this.loggingOut = true;
    this.logoutPending.set(true);
    const pending: Observable<LoginResponse | null> = this.pendingLogin ?? of(null);
    this.clearSession();
    this.status.set('unauthenticated');
    void this.router.navigateByUrl('/login');
    pending
      .pipe(
        catchError(() => of(null)),
        switchMap(() => this.ensureCsrf()),
        switchMap(() => this.http.post<void>('/api/v1/auth/logout', {})),
        finalize(() => {
          this.loggingOut = false;
          this.logoutPending.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.channel?.postMessage('changed');
        },
        error: () => {
          this.status.set('logout-error');
          void this.router.navigateByUrl('/session-error');
        },
      });
  }
  safeReturnUrl(url: string | null): string {
    return url && /^\/(dashboard|categories|transactions|budgets)([?;#]|$)/.test(url)
      ? url
      : '/dashboard';
  }
  private accept(response: LoginResponse): void {
    if (
      !response.user?.id ||
      !Number.isFinite(response.expiresIn) ||
      response.expiresIn <= 0 ||
      response.expiresIn > 3600
    ) {
      throw new Error('Invalid authentication response');
    }
    this.clearSession();
    this.expiresAt = Date.now() + response.expiresIn * 1000;
    this.userState.set(response.user);
    this.status.set('authenticated');
    this.sessionChangesSubject.next();
    this.expiryTimer = setTimeout(() => this.invalidate(), response.expiresIn * 1000);
  }
  private clearSession(): void {
    this.revision++;
    clearTimeout(this.expiryTimer);
    this.expiresAt = 0;
    this.userState.set(null);
    this.sessionChangesSubject.next();
  }
}
