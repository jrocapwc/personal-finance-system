import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth';

@Component({
  selector: 'app-session-error',
  template: `<main style="max-width:32rem;margin:15vh auto;padding:2rem" aria-live="polite">
    <h1>
      {{
        auth.status() === 'logout-error'
          ? 'Logout could not finish'
          : 'Unable to check your session'
      }}
    </h1>
    <p>
      {{
        auth.status() === 'logout-error'
          ? 'Private data has been cleared from this screen. Reconnect and retry to clear the browser session.'
          : 'Check your connection and try again.'
      }}
    </p>
    <button type="button" [disabled]="busy() || auth.logoutPending()" (click)="retry()">
      Retry
    </button>
  </main>`,
})
export class SessionError {
  readonly auth = inject(AuthService);
  readonly busy = signal(false);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  retry(): void {
    if (this.auth.status() === 'logout-error') {
      this.auth.logout();
      return;
    }
    this.busy.set(true);
    this.auth.restoreSession(true).subscribe((status) => {
      this.busy.set(false);
      if (status === 'authenticated')
        void this.router.navigateByUrl(
          this.auth.safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')),
        );
      else if (status === 'unauthenticated')
        void this.router.navigate(['/login'], {
          queryParams: { returnUrl: this.route.snapshot.queryParamMap.get('returnUrl') },
        });
    });
  }
}
