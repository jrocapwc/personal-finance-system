import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const document = inject(DOCUMENT);
  // Resolve and normalize URLs before deciding whether credentials may be sent.
  let url: URL;
  try {
    url = new URL(request.url, document.baseURI);
  } catch {
    return next(request);
  }
  const origin = new URL(document.baseURI).origin;
  const ownApi = url.origin === origin && url.pathname.startsWith('/api/v1/');
  const publicAuth = /^\/api\/v1\/auth\/(login|register)\/?$/.test(url.pathname);
  if (!ownApi || publicAuth) return next(request);
  const token = auth.getToken();
  const outgoing = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(outgoing).pipe(
    catchError((error: unknown) => {
      // A delayed failure from an older session must not log out a new session.
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        token &&
        auth.getToken() === token
      ) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
