import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Transactions } from './transactions';
import { AuthService } from '../../core/services/auth';
import { testCredentials, testLogin } from '../../core/auth.testing';
import {
  financeCategories,
  financeTransaction,
  financePage,
  installDialogTesting,
} from '../finance/finance.testing';

describe('Transactions management', () => {
  let page: Transactions, fixture: ComponentFixture<Transactions>, http: HttpTestingController;
  beforeEach(() => {
    installDialogTesting();
    TestBed.configureTestingModule({
      imports: [Transactions],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    fixture = TestBed.createComponent(Transactions);
    page = fixture.componentInstance;
    fixture.detectChanges();
    TestBed.tick();
    read();
  });
  afterEach(() => {
    try {
      http.verify({ ignoreCancelled: true });
    } finally {
      TestBed.resetTestingModule();
    }
  });
  function read() {
    for (const request of http.match((request) => request.method === 'GET'))
      if (!request.cancelled)
        request.flush(
          request.request.url === '/api/v1/categories'
            ? financeCategories
            : financePage(
                Number(request.request.params.get('page')),
                Number(request.request.params.get('size')),
              ),
        );
    fixture.detectChanges();
  }
  function fill() {
    page.add();
    page.form.patchValue({
      type: 'EXPENSE',
      amount: '125.50',
      transactionDate: '2026-10-26',
      description: 'Lunch',
    });
    page.form.controls.categoryId.setValue(1);
  }
  it('renders backend dates and negative expense display without changing stored amounts', () => {
    expect(fixture.nativeElement.textContent).toContain('Oct 26, 2026');
    expect(fixture.nativeElement.textContent).toContain('-\u20b1125.50');
    expect(page.resource.data()?.content[0].amount).toBe(125.5);
  });
  it('applies all filters and sorting with zero-based pages', () => {
    page.filters.patchValue({
      type: 'EXPENSE',
      categoryId: 1,
      startDate: '2026-10-01',
      endDate: '2026-10-31',
      keyword: 'lunch',
      sort: 'amount',
      direction: 'ASC',
      size: 50,
    });
    page.apply();
    TestBed.tick();
    const request = http.expectOne((request) => request.url === '/api/v1/transactions');
    expect(request.request.params.get('type')).toBe('EXPENSE');
    expect(request.request.params.get('categoryId')).toBe('1');
    expect(request.request.params.get('sort')).toBe('amount');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('50');
    request.flush(financePage(0, 50));
  });
  it('uses the actual page envelope to navigate and resets filters to page zero', () => {
    page.resource.refresh();
    TestBed.tick();
    const request = http.expectOne((request) => request.url === '/api/v1/transactions');
    request.flush({ ...financePage(), totalElements: 21, totalPages: 2, last: false });
    page.changePage(1);
    TestBed.tick();
    const next = http.expectOne((request) => request.url === '/api/v1/transactions');
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...financePage(1), totalElements: 21, totalPages: 2 });
    page.apply();
    TestBed.tick();
    read();
    expect(page.query().page).toBe(0);
  });
  it('creates positive decimal transactions and blocks duplicate submissions', () => {
    fill();
    page.save();
    page.save();
    const request = http.expectOne('/api/v1/transactions');
    expect(request.request.body).toEqual({
      type: 'EXPENSE',
      categoryId: 1,
      amount: 125.5,
      transactionDate: '2026-10-26',
      description: 'Lunch',
    });
    request.flush(financeTransaction);
    TestBed.tick();
    read();
    expect(page.success()).toContain('created');
  });
  it('replaces every mutable field when editing across months and categories', () => {
    page.edit(financeTransaction);
    page.form.controls.type.setValue('INCOME');
    page.form.controls.categoryId.setValue(2);
    page.form.patchValue({ amount: '200.01', transactionDate: '2026-11-01', description: '' });
    page.save();
    const request = http.expectOne('/api/v1/transactions/3');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      type: 'INCOME',
      categoryId: 2,
      amount: 200.01,
      transactionDate: '2026-11-01',
      description: null,
    });
    request.flush({
      ...financeTransaction,
      type: 'INCOME',
      categoryId: 2,
      amount: 200.01,
      transactionDate: '2026-11-01',
      description: null,
    });
    TestBed.tick();
    read();
  });
  it('resets incompatible categories when transaction type changes', () => {
    fill();
    page.form.controls.type.setValue('INCOME');
    expect(page.form.controls.categoryId.value).toBeNull();
    expect(page.formCategories().map((row) => row.id)).toEqual([2]);
    page.form.controls.categoryId.setValue(1);
    page.save();
    http.expectNone((request) => request.method === 'POST');
    expect(page.form.controls.categoryId.hasError('category')).toBe(true);
  });
  it.each(['0', '-1', '1.001', '1e2', '1000000000001'])(
    'blocks invalid or unsupported amount %s',
    (amount) => {
      fill();
      page.form.controls.amount.setValue(amount);
      page.save();
      http.expectNone((request) => request.method === 'POST');
      expect(page.form.invalid).toBe(true);
    },
  );
  it('preserves a failed save and shows a usable error', () => {
    fill();
    page.save();
    http.expectOne('/api/v1/transactions').flush({}, { status: 400, statusText: 'Bad Request' });
    expect(page.form.controls.amount.value).toBe('125.50');
    expect(page.form.controls.description.value).toBe('Lunch');
    expect(page.editorError()).toContain('check your form');
    expect(page.busy()).toBe(false);
  });
  it('confirms deletion, handles 204 and refreshes the table', () => {
    page.askDelete(financeTransaction);
    http.expectNone((request) => request.method === 'DELETE');
    page.remove();
    http.expectOne('/api/v1/transactions/3').flush(null, { status: 204, statusText: 'No Content' });
    TestBed.tick();
    read();
    expect(page.success()).toContain('deleted');
  });
  it('moves back to a valid page after deleting its final transaction', () => {
    page.query.set({ ...page.query(), page: 1 });
    TestBed.tick();
    read();
    page.askDelete(financeTransaction);
    page.remove();
    http.expectOne('/api/v1/transactions/3').flush(null, { status: 204, statusText: 'No Content' });
    TestBed.tick();
    for (const request of http.match((request) => request.method === 'GET'))
      request.flush(
        request.request.url === '/api/v1/categories'
          ? financeCategories
          : { ...financePage(1), content: [], totalElements: 0, totalPages: 0 },
      );
    TestBed.tick();
    read();
    expect(page.query().page).toBe(0);
  });
  it('rejects reversed filter dates without issuing a request', () => {
    page.filters.patchValue({ startDate: '2026-11-01', endDate: '2026-10-01' });
    page.apply();
    TestBed.tick();
    http.expectNone((request) => request.url === '/api/v1/transactions');
    expect(page.filters.hasError('dateRange')).toBe(true);
  });
  it('cancels superseded searches and refuses old results', () => {
    page.filters.controls.keyword.setValue('first');
    page.apply();
    TestBed.tick();
    const old = http.expectOne((request) => request.url === '/api/v1/transactions');
    page.filters.controls.keyword.setValue('second');
    page.apply();
    expect(page.resource.data()).toBeNull();
    TestBed.tick();
    expect(old.cancelled).toBe(true);
    read();
  });
  it('clears forms, results and pending edits on logout', () => {
    fill();
    page.save();
    const pending = http.expectOne('/api/v1/transactions');
    TestBed.inject(AuthService).logout();
    expect(page.resource.data()).toBeNull();
    expect(page.categoryResource.data()).toBeNull();
    TestBed.tick();
    expect(pending.cancelled).toBe(true);
    expect(page.form.controls.description.value).toBe('');
    expect(page.form.controls.categoryId.value).toBeNull();
  });
});
