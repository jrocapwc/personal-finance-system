export class FinanceNumberError extends Error {
  constructor() {
    super(
      'This record cannot be represented safely. Amounts must be within PHP 1 trillion. Please contact support if the record remains unavailable.',
    );
  }
}
export function safeId(id: number): number {
  if (!Number.isSafeInteger(id) || id <= 0) throw new FinanceNumberError();
  return id;
}
export function safeAmount(amount: number): number {
  if (
    !Number.isFinite(amount) ||
    amount <= 0 ||
    amount > 1e12 ||
    Number(amount.toFixed(2)) !== amount
  )
    throw new FinanceNumberError();
  return amount;
}
export function checkedFinance<T>(data: T): T {
  function check(value: unknown): void {
    if (Array.isArray(value)) {
      value.forEach(check);
      return;
    }
    if (!value || typeof value !== 'object') return;
    for (const [key, item] of Object.entries(value)) {
      if (key === 'id' || key === 'categoryId') safeId(item as number);
      if (
        ['amount', 'monthlyLimit', 'spentAmount', 'remainingAmount'].includes(key) &&
        (typeof item !== 'number' || !Number.isFinite(item) || Math.abs(item) > 1e12)
      )
        throw new FinanceNumberError();
      if (
        ['page', 'size', 'totalElements', 'totalPages'].includes(key) &&
        (!Number.isSafeInteger(item) || Number(item) < 0)
      )
        throw new FinanceNumberError();
      if (key === 'percentageUsed' && (typeof item !== 'number' || !Number.isFinite(item)))
        throw new FinanceNumberError();
      check(item);
    }
  }
  check(data);
  return data;
}
