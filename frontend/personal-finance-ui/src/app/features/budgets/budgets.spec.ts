import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Budgets } from './budgets';
import { AuthService } from '../../core/services/auth';
import { testCredentials, testLogin } from '../../core/auth.testing';
import { financeCategories, financeBudget, installDialogTesting } from '../finance/finance.testing';

describe('Budget management', () => {
  let page: Budgets, fixture: ComponentFixture<Budgets>, http: HttpTestingController;
  beforeEach(() => {
    installDialogTesting();
    TestBed.configureTestingModule({
      imports: [Budgets],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    fixture = TestBed.createComponent(Budgets);
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
          request.request.url === '/api/v1/categories' ? [financeCategories[0]] : [financeBudget],
        );
    fixture.detectChanges();
  }
  function fill() {
    page.add();
    page.form.setValue({
      categoryId: 1,
      month: 10,
      year: 2026,
      monthlyLimit: '100.00',
      alertThreshold: 80,
    });
  }
  it('requests monthly budgets and expense-only categories', () => {
    expect(page.categoryResource.data()?.map((row) => row.type)).toEqual(['EXPENSE']);
    page.periodForm.setValue({ month: 1, year: 2027 });
    page.applyPeriod();
    TestBed.tick();
    http.expectOne('/api/v1/budgets?month=1&year=2027').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No budgets set');
  });
  it('displays real >100% usage and negative remaining amounts with a clamped bar', () => {
    expect(fixture.nativeElement.textContent).toContain('125.50%');
    expect(fixture.nativeElement.textContent).toContain('-\u20b125.50');
    const bar = fixture.nativeElement.querySelector('[role="progressbar"]');
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
    expect(bar.getAttribute('aria-valuetext')).toContain('125.5%');
    expect(bar.querySelector('span').style.width).toBe('100%');
    expect(page.rows()[0].percentageUsed).toBe(125.5);
  });
  it('creates a budget without client-calculated totals', () => {
    fill();
    page.save();
    page.save();
    const request = http.expectOne('/api/v1/budgets');
    expect(request.request.body).toEqual({
      categoryId: 1,
      month: 10,
      year: 2026,
      monthlyLimit: 100,
      alertThreshold: 80,
    });
    request.flush(financeBudget);
    TestBed.tick();
    read();
    expect(page.success()).toContain('created');
  });
  it('edits all budget fields including moving to another month', () => {
    page.edit(financeBudget);
    page.form.patchValue({ month: 11, monthlyLimit: '150.25', alertThreshold: 90 });
    page.save();
    const request = http.expectOne('/api/v1/budgets/4');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      categoryId: 1,
      month: 11,
      year: 2026,
      monthlyLimit: 150.25,
      alertThreshold: 90,
    });
    request.flush({ ...financeBudget, month: 11, monthlyLimit: 150.25, alertThreshold: 90 });
    TestBed.tick();
    read();
  });
  it('requires deletion confirmation and handles 204', () => {
    page.askDelete(financeBudget);
    http.expectNone((request) => request.method === 'DELETE');
    page.remove();
    http.expectOne('/api/v1/budgets/4').flush(null, { status: 204, statusText: 'No Content' });
    TestBed.tick();
    read();
    expect(page.success()).toContain('deleted');
  });
  it('explains duplicate category/period budget conflicts and preserves input', () => {
    fill();
    page.save();
    http
      .expectOne('/api/v1/budgets')
      .flush({ code: 'DUPLICATE_BUDGET' }, { status: 409, statusText: 'Conflict' });
    expect(page.editorError()).toContain('already exists for this category, month and year');
    expect(page.form.controls.monthlyLimit.value).toBe('100.00');
  });
  it('allows only an available expense category', () => {
    fill();
    page.form.controls.categoryId.setValue(2);
    page.save();
    http.expectNone((request) => request.method === 'POST');
    expect(page.form.controls.categoryId.hasError('category')).toBe(true);
  });
  it.each([0, 100, 80.5])('rejects invalid threshold %s', (threshold) => {
    fill();
    page.form.controls.alertThreshold.setValue(threshold);
    page.save();
    http.expectNone((request) => request.method === 'POST');
    expect(page.form.invalid).toBe(true);
  });
  it('rejects invalid limits and years', () => {
    fill();
    page.form.patchValue({ monthlyLimit: '1.001', year: 2101 });
    page.save();
    http.expectNone((request) => request.method === 'POST');
    expect(page.form.invalid).toBe(true);
  });
  it('clears all finance data and dialogs after logout', () => {
    fill();
    TestBed.inject(AuthService).logout();
    expect(page.resource.data()).toBeNull();
    TestBed.tick();
    expect(page.form.controls.monthlyLimit.value).toBe('');
    expect(fixture.nativeElement.querySelector('#budget-editor-title').closest('dialog').open).toBe(
      false,
    );
  });
});
