import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, ValidatorFn } from '@angular/forms';
import { ApiError } from '../../core/models/api-error.model';
import { FinanceNumberError } from '../../core/models/finance-safety';
export const nonBlank: ValidatorFn = (control) =>
  !String(control.value ?? '').trim() ? { required: true } : null;
export const integer: ValidatorFn = (control) =>
  Number.isInteger(control.value) ? null : { integer: true };
export const positiveMoney: ValidatorFn = (control) => {
  const value = String(control.value ?? '');
  if (!/^\d{1,17}(?:\.\d{1,2})?$/.test(value) || Number(value) <= 0) return { money: true };
  return Number(value) > 1e12 ? { moneyRange: true } : null;
};
export const calendarDate: ValidatorFn = (control) => {
  const value = String(control.value ?? '');
  if (!value) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { date: true };
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
    ? null
    : { date: true };
};
export const dateRange: ValidatorFn = (control) => {
  const start = control.get('startDate')?.value,
    end = control.get('endDate')?.value;
  return start && end && start > end ? { dateRange: true } : null;
};
export function invalid(control: AbstractControl): boolean {
  return control.touched && control.invalid;
}
export function crudError(error: unknown): string {
  if (error instanceof FinanceNumberError) return error.message;
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    switch (body?.code) {
      case 'DUPLICATE_CATEGORY':
        return 'A category with this name and type already exists.';
      case 'CATEGORY_IN_USE':
        return 'This category is used by transactions or budgets. It cannot be deleted or change type.';
      case 'DUPLICATE_BUDGET':
        return 'A budget already exists for this category, month and year.';
    }
    if (error.status === 409)
      return 'This change conflicts with an existing record or a category in use.';
    if (error.status === 404)
      return 'This record or its category is no longer available. Refresh and try again.';
    if (error.status === 400)
      return 'Please check your form values and category selection, then try again.';
    if (error.status === 401) return 'Your session has ended. Please sign in again.';
    if (error.status === 403) return 'You do not have permission to perform this action.';
    if (error.status === 0)
      return 'Unable to reach the finance API. Check your connection and try again.';
  }
  return 'The request could not be completed. Please try again.';
}
