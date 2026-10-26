import { ValidatorFn } from '@angular/forms';

export const trimmedNameLength: ValidatorFn = (control) =>
  (control.value as string).trim().length > 100 ? { maxlength: true } : null;

export const passwordChangeRules: ValidatorFn = (form) => {
  const current = form.get('currentPassword')?.value;
  const password = form.get('newPassword')?.value;
  const confirm = form.get('confirmPassword')?.value;
  const errors: Record<string, boolean> = {};
  if (password && current === password) errors['passwordReused'] = true;
  if (confirm && password !== confirm) errors['passwordMismatch'] = true;
  return Object.keys(errors).length ? errors : null;
};
