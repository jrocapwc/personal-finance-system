import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { nonBlank, passwordsMatch, utf8PasswordLimit } from '../auth.validators';
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, MatButtonModule],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly hidePassword = signal(true);
  readonly form = this.fb.nonNullable.group(
    {
      name: ['', [Validators.required, nonBlank, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, nonBlank, Validators.minLength(12), utf8PasswordLimit]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.touched && control.invalid;
  }
  submit(): void {
    if (this.loading()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error.set('');
    this.loading.set(true);
    const values = this.form.getRawValue();
    this.auth
      .register({ name: values.name.trim(), email: values.email.trim(), password: values.password })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          void this.router.navigate(['/login'], { queryParams: { registered: 'true' } });
        },
        error: (error: unknown) => {
          this.error.set(
            error instanceof HttpErrorResponse && error.status === 409
              ? 'An account with this email already exists. Please sign in.'
              : error instanceof HttpErrorResponse && error.status === 400
                ? 'Please check your details and password requirements.'
                : 'Registration is unavailable. Please try again shortly.',
          );
        },
      });
  }
}
