import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, Subject, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private token: string | null = null;
  private expiresAt = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private revision = 0;
  private readonly userState = signal<UserResponse | null>(null);
  readonly currentUser = this.userState.asReadonly();
  private readonly sessionChangesSubject = new Subject<void>();
  // Synchronous notification lets private views cancel work before another session starts.
  readonly sessionChanges = this.sessionChangesSubject.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearSession());
  }

  register({ name, email, password }: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>('/api/v1/auth/register', { name, email, password });
  }

  login({ email, password }: LoginRequest): Observable<LoginResponse> {
    this.clearSession();
    const revision = this.revision;
    return this.http.post<LoginResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap((response) => {
        // A logout while the request is in flight must not restore the session.
        if (revision !== this.revision) return;
        if (
          !response.accessToken ||
          response.tokenType !== 'Bearer' ||
          !Number.isFinite(response.expiresIn) ||
          response.expiresIn <= 0 ||
          response.expiresIn > 2147483 ||
          !response.user
        ) {
          throw new Error('Invalid authentication response');
        }
        this.token = response.accessToken;
        this.expiresAt = Date.now() + response.expiresIn * 1000;
        this.userState.set(response.user);
        this.sessionChangesSubject.next();
        this.expiryTimer = setTimeout(() => this.logout(), response.expiresIn * 1000);
      }),
    );
  }

  getToken(): string | null {
    if (this.token && Date.now() >= this.expiresAt) this.logout();
    return this.token;
  }
  getAccessToken(): string | null {
    return this.getToken();
  }
  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  private clearSession(): void {
    this.revision++;
    clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    this.token = null;
    this.expiresAt = 0;
    this.userState.set(null);
    this.sessionChangesSubject.next();
  }
}
