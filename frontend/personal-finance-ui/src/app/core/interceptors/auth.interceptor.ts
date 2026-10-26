import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn, HttpXsrfTokenExtractor } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, filter, switchMap, takeUntil, throwError } from 'rxjs';
import { AuthService } from '../services/auth';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const document = inject(DOCUMENT);
  let url: URL;
  try {
    url = new URL(request.url, document.baseURI);
  } catch {
    return next(request);
  }
  const ownApi =
    url.origin === new URL(document.baseURI).origin && url.pathname.startsWith('/api/v1/');
  if (!ownApi) return next(request);
  const xsrf = inject(HttpXsrfTokenExtractor);
  const lifecycle = /^\/api\/v1\/auth\/(login|register|csrf|logout|session)\/?$/.test(url.pathname);
  if (!lifecycle) auth.isAuthenticated();
  const revision = auth.sessionRevision;
  const outgoing = request.clone({ transferCache: false });
  const needsCsrf = !['GET', 'HEAD', 'OPTIONS'].includes(request.method);
  // A restored browser session can have lost its readable CSRF cookie. Bootstrap
  // before dispatching the mutation; never retry an already submitted write.
  const response =
    !lifecycle && needsCsrf && !xsrf.getToken()
      ? auth.ensureCsrf().pipe(
          switchMap(() => {
            const token = xsrf.getToken();
            if (!token)
              return throwError(
                () => new Error('Unable to initialize request protection. Refresh and retry.'),
              );
            return next(outgoing.clone({ setHeaders: { 'X-XSRF-TOKEN': token } }));
          }),
        )
      : next(outgoing);
  if (lifecycle) return response;
  return response.pipe(
    takeUntil(auth.sessionChanges.pipe(filter(() => auth.sessionRevision !== revision))),
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        revision === auth.sessionRevision
      )
        auth.invalidate();
      return throwError(() => error);
    }),
  );
};
