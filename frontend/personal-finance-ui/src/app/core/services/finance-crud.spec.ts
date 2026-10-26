import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth';
import { CategoryService } from './category';
import { TransactionService } from './transaction';
import { BudgetService } from './budget';
import { FinanceChanges } from './finance-changes';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { testCredentials, testLogin } from '../auth.testing';
import {
  financeCategories,
  financeBudget,
  financeTransaction,
  financePage,
} from '../../features/finance/finance.testing';
import { FinanceNumberError } from '../models/finance-safety';

describe('Finance CRUD HTTP contracts', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // HttpTestingController does not apply the server's readable Set-Cookie.
    document.cookie = 'XSRF-TOKEN=test-csrf; Path=/';
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush(testLogin);
  });
  afterEach(() => http.verify());
  const categoryBody = { name: 'Food', type: 'EXPENSE' as const, color: null };
  const transactionBody = {
    categoryId: 1,
    type: 'EXPENSE' as const,
    amount: 125.5,
    description: null,
    transactionDate: '2026-10-26',
  };
  const budgetBody = {
    categoryId: 1,
    month: 10,
    year: 2026,
    monthlyLimit: 100,
    alertThreshold: 80,
  };
  function check(url: string, method: string, body: unknown, response: unknown) {
    const request = http.expectOne(url);
    expect(request.request.method).toBe(method);
    expect(request.request.body).toEqual(body);
    expect(request.request.headers.get('Authorization')).toBeNull();
    expect(request.request.params.has('userId')).toBe(false);
    request.flush(
      response as object | null,
      method === 'DELETE' ? { status: 204, statusText: 'No Content' } : undefined,
    );
  }
  it('uses only the category collection with the supported optional type', () => {
    TestBed.inject(CategoryService).list('INCOME').subscribe();
    check(
      '/api/v1/categories?type=INCOME',
      'GET',
      null,
      financeCategories.filter((row) => row.type === 'INCOME'),
    );
  });
  it('creates a category with only allowed DTO fields', () => {
    TestBed.inject(CategoryService)
      .create({ ...categoryBody, userId: 'ignored' } as never)
      .subscribe();
    check('/api/v1/categories', 'POST', categoryBody, financeCategories[0]);
  });
  it('updates categories with full PUT replacement including nullable color', () => {
    TestBed.inject(CategoryService).update(1, categoryBody).subscribe();
    check('/api/v1/categories/1', 'PUT', categoryBody, financeCategories[0]);
  });
  it.each(['category', 'transaction', 'budget'])(
    'handles 204 %s deletion and emits refresh',
    (feature) => {
      const changes = TestBed.inject(FinanceChanges);
      const version = changes.revision();
      if (feature === 'category')
        TestBed.inject(CategoryService)
          .delete(1)
          .subscribe((value) => expect(value).toBeNull());
      if (feature === 'transaction')
        TestBed.inject(TransactionService)
          .delete(1)
          .subscribe((value) => expect(value).toBeNull());
      if (feature === 'budget')
        TestBed.inject(BudgetService)
          .delete(1)
          .subscribe((value) => expect(value).toBeNull());
      check(
        `/api/v1/${feature === 'category' ? 'categories' : feature === 'transaction' ? 'transactions' : 'budgets'}/1`,
        'DELETE',
        null,
        null,
      );
      expect(changes.revision()).toBe(version + 1);
    },
  );
  it('serializes every supported transaction filter and zero-based pagination exactly', () => {
    TestBed.inject(TransactionService)
      .list({
        type: 'EXPENSE',
        categoryId: 1,
        startDate: '2026-10-01',
        endDate: '2026-10-31',
        keyword: 'food & bills',
        page: 0,
        size: 100,
        sort: 'amount',
        direction: 'ASC',
      })
      .subscribe();
    const request = http.expectOne((request) => request.url === '/api/v1/transactions');
    expect(request.request.params.keys().sort()).toEqual(
      [
        'type',
        'categoryId',
        'startDate',
        'endDate',
        'keyword',
        'page',
        'size',
        'sort',
        'direction',
      ].sort(),
    );
    expect(request.request.params.get('keyword')).toBe('food & bills');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('100');
    expect(request.request.params.get('sort')).toBe('amount');
    expect(request.request.params.get('direction')).toBe('ASC');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(financePage());
  });
  it.each(['create', 'update'] as const)(
    'sends a complete transaction %s with a positive decimal amount',
    (operation) => {
      const api = TestBed.inject(TransactionService);
      (operation === 'create'
        ? api.create({ ...transactionBody, source: 'ignored' } as never)
        : api.update(3, transactionBody)
      ).subscribe();
      check(
        '/api/v1/transactions' + (operation === 'update' ? '/3' : ''),
        operation === 'update' ? 'PUT' : 'POST',
        transactionBody,
        financeTransaction,
      );
    },
  );
  it('reads transaction detail using the actual endpoint', () => {
    TestBed.inject(TransactionService).get(3).subscribe();
    check('/api/v1/transactions/3', 'GET', null, financeTransaction);
  });
  it('requires both monthly budget parameters', () => {
    TestBed.inject(BudgetService).list(10, 2026).subscribe();
    check('/api/v1/budgets?month=10&year=2026', 'GET', null, [financeBudget]);
  });
  it.each(['create', 'update'] as const)(
    'sends full budget %s DTOs without calculated fields',
    (operation) => {
      const api = TestBed.inject(BudgetService);
      (operation === 'create'
        ? api.create({ ...budgetBody, spentAmount: 99 } as never)
        : api.update(4, budgetBody)
      ).subscribe();
      check(
        '/api/v1/budgets' + (operation === 'update' ? '/4' : ''),
        operation === 'update' ? 'PUT' : 'POST',
        budgetBody,
        financeBudget,
      );
    },
  );
  it('reads budget detail using the actual endpoint', () => {
    TestBed.inject(BudgetService).get(4).subscribe();
    check('/api/v1/budgets/4', 'GET', null, financeBudget);
  });
  it('does not emit a refresh on failed mutations', () => {
    const changes = TestBed.inject(FinanceChanges);
    const version = changes.revision();
    TestBed.inject(CategoryService)
      .create(categoryBody)
      .subscribe({ error: () => {} });
    http
      .expectOne('/api/v1/categories')
      .flush({ code: 'DUPLICATE_CATEGORY' }, { status: 409, statusText: 'Conflict' });
    expect(changes.revision()).toBe(version);
  });
  it('never notifies a new session about an old mutation response', () => {
    const changes = TestBed.inject(FinanceChanges);
    const version = changes.revision();
    TestBed.inject(CategoryService).create(categoryBody).subscribe();
    const pending = http.expectOne('/api/v1/categories');
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush({ ...testLogin, user: { ...testLogin.user } });
    expect(pending.cancelled).toBe(true);
    expect(changes.revision()).toBe(version);
  });
  it('rejects unsafe identifiers before making a request', () => {
    expect(() => TestBed.inject(TransactionService).get(9007199254740992)).toThrow(
      FinanceNumberError,
    );
    expect(() => TestBed.inject(BudgetService).delete(1.5)).toThrow(FinanceNumberError);
  });
  it('rejects unsafe identifiers in responses rather than rounding them', () => {
    let error: unknown;
    TestBed.inject(CategoryService)
      .list()
      .subscribe({ error: (value) => (error = value) });
    http.expectOne('/api/v1/categories').flush([{ ...financeCategories[0], id: 9007199254740992 }]);
    expect(error).toBeInstanceOf(FinanceNumberError);
  });
  it('rejects unsafe or excessive-scale money before sending it', () => {
    for (const amount of [0, -1, 0.001, 1e12 + 1, Infinity])
      expect(() =>
        TestBed.inject(TransactionService).create({ ...transactionBody, amount }),
      ).toThrow(FinanceNumberError);
  });
});
