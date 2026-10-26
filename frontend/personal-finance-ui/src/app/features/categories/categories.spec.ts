import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Categories } from './categories';
import { AuthService } from '../../core/services/auth';
import { testCredentials, testLogin } from '../../core/auth.testing';
import { financeCategories, installDialogTesting } from '../finance/finance.testing';

describe('Categories management', () => {
  let page: Categories, fixture: ComponentFixture<Categories>, http: HttpTestingController;
  beforeEach(() => {
    installDialogTesting();
    TestBed.configureTestingModule({
      imports: [Categories],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    fixture = TestBed.createComponent(Categories);
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
    for (const request of http.match((request) => request.method === 'GET')) {
      if (!request.cancelled)
        request.flush(
          financeCategories.filter(
            (row) =>
              !request.request.params.get('type') ||
              row.type === request.request.params.get('type'),
          ),
        );
    }
    fixture.detectChanges();
  }
  it('lists categories and applies the actual INCOME filter', () => {
    expect(fixture.nativeElement.textContent).toContain('Food');
    page.setFilter('INCOME');
    TestBed.tick();
    const request = http.expectOne('/api/v1/categories?type=INCOME');
    request.flush([financeCategories[1]]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Salary');
    expect(fixture.nativeElement.querySelectorAll('.record-card')).toHaveLength(1);
  });
  it('creates a category and prevents duplicate submissions', () => {
    page.add();
    page.form.setValue({ name: ' Education ', type: 'EXPENSE', color: '' });
    page.save();
    page.save();
    const request = http.expectOne('/api/v1/categories');
    expect(request.request.body).toEqual({ name: 'Education', type: 'EXPENSE', color: null });
    expect(page.busy()).toBe(true);
    request.flush({ ...financeCategories[0], id: 9, name: 'Education' });
    TestBed.tick();
    read();
    expect(page.success()).toContain('created');
    expect(
      fixture.nativeElement.querySelector('#category-editor-title').closest('dialog').open,
    ).toBe(false);
  });
  it('edits the complete category and clears an omitted color', () => {
    page.edit(financeCategories[0]);
    page.form.controls.color.setValue('');
    page.form.controls.name.setValue('Dining');
    page.save();
    const request = http.expectOne('/api/v1/categories/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Dining', type: 'EXPENSE', color: null });
    request.flush({ ...financeCategories[0], name: 'Dining', color: null });
    TestBed.tick();
    read();
    expect(page.success()).toContain('updated');
  });
  it('requires confirmation before issuing DELETE and handles 204', () => {
    page.askDelete(financeCategories[0]);
    http.expectNone((request) => request.method === 'DELETE');
    expect(
      fixture.nativeElement.querySelector('#category-delete-title').closest('dialog').open,
    ).toBe(true);
    page.remove();
    http.expectOne('/api/v1/categories/1').flush(null, { status: 204, statusText: 'No Content' });
    TestBed.tick();
    read();
    expect(page.success()).toContain('deleted');
  });
  it('retains entered data after duplicate conflicts', () => {
    page.add();
    page.form.setValue({ name: 'Food', type: 'EXPENSE', color: '#abc' });
    page.save();
    http
      .expectOne('/api/v1/categories')
      .flush({ code: 'DUPLICATE_CATEGORY' }, { status: 409, statusText: 'Conflict' });
    expect(page.form.controls.name.value).toBe('Food');
    expect(page.editorError()).toContain('already exists');
    expect(page.busy()).toBe(false);
  });
  it('explains referenced-category restrictions for deletion and type changes', () => {
    page.askDelete(financeCategories[0]);
    page.remove();
    http
      .expectOne('/api/v1/categories/1')
      .flush({ code: 'CATEGORY_IN_USE' }, { status: 409, statusText: 'Conflict' });
    expect(page.deleteError()).toContain('used by transactions or budgets');
    page.confirmation?.close();
    page.edit(financeCategories[0]);
    page.form.controls.type.setValue('INCOME');
    page.save();
    http
      .expectOne('/api/v1/categories/1')
      .flush({ code: 'CATEGORY_IN_USE' }, { status: 409, statusText: 'Conflict' });
    expect(page.editorError()).toContain('cannot be deleted or change type');
  });
  it('blocks blank names and invalid colors before HTTP', () => {
    page.add();
    page.form.setValue({ name: '   ', type: 'EXPENSE', color: 'red' });
    page.save();
    http.expectNone((request) => request.method === 'POST');
    expect(page.form.invalid).toBe(true);
  });
  it('clears sensitive forms and lists and cancels mutation on logout', () => {
    page.add();
    page.form.setValue({ name: 'Private category', type: 'EXPENSE', color: '' });
    page.save();
    const request = http.expectOne('/api/v1/categories');
    TestBed.inject(AuthService).logout();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
    expect(page.resource.data()).toBeNull();
    TestBed.tick();
    expect(request.cancelled).toBe(true);
    expect(page.form.controls.name.value).toBe('');
    expect(page.deleting()).toBeNull();
  });
  it('shows a helpful empty result', () => {
    page.resource.refresh();
    TestBed.tick();
    http.expectOne('/api/v1/categories').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No categories match');
  });
});
