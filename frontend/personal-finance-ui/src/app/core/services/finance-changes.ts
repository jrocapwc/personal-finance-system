import { inject, Injectable, signal } from '@angular/core';
import { AuthService } from './auth';
@Injectable({ providedIn: 'root' })
export class FinanceChanges {
  private readonly auth = inject(AuthService);
  private readonly version = signal(0);
  readonly revision = this.version.asReadonly();
  // This counter contains no financial records or user identifiers.
  track() {
    const owner = this.auth.currentUser();
    return () => {
      if (owner && owner === this.auth.currentUser()) this.version.update((value) => value + 1);
    };
  }
}
