import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import { nonBlank, utf8PasswordLimit } from '../auth/auth.validators';
import { passwordChangeRules, trimmedNameLength } from './account-settings.validators';

type PasswordField = 'currentPassword' | 'newPassword' | 'confirmPassword';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule],
  templateUrl: './account-settings.html',
  styleUrl: './account-settings.scss',
})
export class AccountSettings {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly destroy = inject(DestroyRef);
  private readonly snack = inject(MatSnackBar);
  readonly user = this.auth.currentUser;
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly changingPassword = signal(false);
  readonly profileError = signal('');
  readonly passwordError = signal('');
  readonly success = signal('');
  readonly hidden = signal<Record<PasswordField, boolean>>({
    currentPassword: true,
    newPassword: true,
    confirmPassword: true,
  });
  readonly busy = computed(() => this.saving() || this.changingPassword());
  readonly initials = computed(() =>
    (this.user()?.name ?? '')
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase(),
  );
  readonly profileForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, nonBlank, trimmedNameLength]],
  });
  readonly passwordForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required, nonBlank, utf8PasswordLimit]],
      newPassword: [
        '',
        [Validators.required, nonBlank, Validators.minLength(12), utf8PasswordLimit],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordChangeRules },
  );
  readonly passwordFields: { key: PasswordField; label: string; autocomplete: string }[] = [
    { key: 'currentPassword', label: 'Current Password', autocomplete: 'current-password' },
    { key: 'newPassword', label: 'New Password', autocomplete: 'new-password' },
    { key: 'confirmPassword', label: 'Confirm New Password', autocomplete: 'new-password' },
  ];

  constructor() {
    this.auth.sessionChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => {
      if (!this.auth.currentUser()) {
        this.passwordForm.reset();
        this.hidden.set({ currentPassword: true, newPassword: true, confirmPassword: true });
      }
    });
    this.load();
    this.destroy.onDestroy(() => this.passwordForm.reset());
  }

  load(): void {
    if (this.loading() || this.busy()) return;
    this.loading.set(true);
    this.loadError.set('');
    this.auth
      .loadProfile()
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (user) => this.profileForm.reset({ name: user.name }),
        error: (error: unknown) => this.loadError.set(this.message(error)),
      });
  }

  edit(): void {
    if (this.busy()) return;
    this.profileForm.reset({ name: this.user()?.name ?? '' });
    this.profileError.set('');
    this.success.set('');
    this.editing.set(true);
  }

  cancel(): void {
    if (this.busy()) return;
    this.profileForm.reset({ name: this.user()?.name ?? '' });
    this.profileError.set('');
    this.editing.set(false);
  }

  save(): void {
    if (this.busy() || !this.editing()) return;
    this.profileForm.controls.name.setValue(this.profileForm.controls.name.value.trim());
    this.profileForm.markAllAsTouched();
    if (this.profileForm.invalid) return;
    this.saving.set(true);
    this.profileError.set('');
    this.success.set('');
    this.auth
      .updateProfile(this.profileForm.controls.name.value)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.editing.set(false);
          this.success.set('Your profile has been updated.');
        },
        error: (error: unknown) => this.profileError.set(this.message(error)),
      });
  }

  toggle(field: PasswordField): void {
    this.hidden.update((hidden) => ({ ...hidden, [field]: !hidden[field] }));
  }

  fieldError(field: PasswordField): string {
    const control = this.passwordForm.controls[field];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('minlength')) return 'Use at least 12 characters.';
    if (control.hasError('utf8Bytes'))
      return 'Use no more than 72 UTF-8 bytes. Some characters use multiple bytes.';
    if (field === 'newPassword' && this.passwordForm.hasError('passwordReused'))
      return 'Choose a password different from your current password.';
    if (field === 'confirmPassword' && this.passwordForm.hasError('passwordMismatch'))
      return 'Passwords do not match.';
    return '';
  }

  changePassword(): void {
    if (this.busy()) return;
    this.passwordForm.markAllAsTouched();
    if (this.passwordForm.invalid) return;
    this.changingPassword.set(true);
    this.passwordError.set('');
    this.success.set('');
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.auth
      .changePassword(currentPassword, newPassword)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.changingPassword.set(false)),
      )
      .subscribe({
        next: () => {
          this.passwordForm.reset();
          this.hidden.set({ currentPassword: true, newPassword: true, confirmPassword: true });
          this.snack.open(
            'Password updated. Please sign in again with your new password.',
            'Dismiss',
            { duration: 10000 },
          );
          this.auth.passwordChanged();
        },
        error: (error: unknown) => this.passwordError.set(this.message(error, true)),
      });
  }

  private message(error: unknown, password = false): string {
    if (!(error instanceof HttpErrorResponse))
      return 'Unable to complete the request. Refresh the page and try again.';
    if (error.status === 401) {
      this.passwordForm.reset();
      this.snack.open('Your session has expired. Please sign in again.', 'Dismiss', {
        duration: 8000,
      });
      return 'Your session has expired. Please sign in again.';
    }
    if (error.status === 400 && password && error.error?.code === 'INVALID_PASSWORD_CHANGE') {
      return 'Password change rejected. Check your current password and choose a different new password.';
    }
    // Only fixed, safe text is displayed; backend messages and rejected values are never rendered.
    return (
      (
        {
          0: 'Unable to connect. Check your internet connection and try again.',
          400: 'Please check your details and the requirements shown below.',
          403: 'Request protection could not be verified, or access was denied. Refresh the page before trying again.',
          404: 'This account-management feature is unavailable on the server. Please try again later.',
          409: 'Your account could not be updated because of a conflict. Reload your profile and try again.',
        } as Record<number, string>
      )[error.status] ?? 'An unexpected server error occurred. Please try again later.'
    );
  }
}
