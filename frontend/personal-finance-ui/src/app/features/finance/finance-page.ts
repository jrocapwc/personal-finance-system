import { DestroyRef, Directive, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Observable, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import { crudError, invalid } from './finance-form';
@Directive()
export abstract class FinancePage {
  readonly auth = inject(AuthService);
  readonly busy = signal(false);
  readonly editorError = signal('');
  readonly deleteError = signal('');
  readonly success = signal('');
  readonly invalid = invalid;
  private readonly destroyRef = inject(DestroyRef);
  private pending = new Subscription();
  constructor() {
    effect(() => {
      this.auth.currentUser();
      this.pending.unsubscribe();
      this.pending = new Subscription();
      this.busy.set(false);
      this.editorError.set('');
      this.deleteError.set('');
      this.success.set('');
      untracked(() => this.clearSessionForms());
    });
    this.destroyRef.onDestroy(() => this.pending.unsubscribe());
  }
  protected abstract clearSessionForms(): void;
  protected mutate<T>(
    request: Observable<T>,
    completed: (data: T) => void,
    deleting = false,
  ): void {
    if (this.busy() || !this.auth.isAuthenticated()) return;
    const owner = this.auth.currentUser();
    const target = deleting ? this.deleteError : this.editorError;
    target.set('');
    this.success.set('');
    this.busy.set(true);
    this.pending.add(
      request
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => {
            if (owner === this.auth.currentUser()) this.busy.set(false);
          }),
        )
        .subscribe({
          next: (data) => {
            if (owner === this.auth.currentUser()) {
              this.busy.set(false);
              completed(data);
            }
          },
          error: (error) => {
            if (owner === this.auth.currentUser()) target.set(crudError(error));
          },
        }),
    );
  }
}
