import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
export const nonBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && !control.value.trim() ? { required: true } : null;
export const utf8PasswordLimit: ValidatorFn = (control) =>
  new TextEncoder().encode(control.value ?? '').length > 72 ? { utf8Bytes: true } : null;
export const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value !== control.get('confirmPassword')?.value
    ? { passwordMismatch: true }
    : null;
