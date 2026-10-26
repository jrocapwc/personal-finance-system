import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BankConnections } from './bank-connections';
import { BankService, DemoConnection, StatementPreview } from '../../core/services/bank';
import { CategoryService } from '../../core/services/category';
const demo: DemoConnection = {
  provider: 'Harbor Demo Bank',
  accountName: 'Fictional Everyday',
  maskedAccountNumber: '0042',
  simulated: true,
  connected: false,
  demoBalance: '100.00',
  currency: 'PHP',
  lastSync: null,
};
function statement(): StatementPreview {
  return {
    id: 'test-preview',
    expiresAt: '2026-10-26T12:00:00Z',
    headers: ['Date', 'Description', 'Amount'],
    rows: [
      {
        rowNumber: 1,
        transactionDate: '2026-10-01',
        description: '<img src=x onerror=alert(1)> Food',
        type: 'EXPENSE',
        amount: '12.34',
        validationError: null,
        duplicate: false,
        suggestedCategoryId: null,
      },
      {
        rowNumber: 2,
        transactionDate: null,
        description: 'Invalid',
        type: null,
        amount: null,
        validationError: 'Invalid date',
        duplicate: false,
        suggestedCategoryId: null,
      },
      {
        rowNumber: 3,
        transactionDate: '2026-10-01',
        description: 'Duplicate',
        type: 'EXPENSE',
        amount: '12.34',
        validationError: null,
        duplicate: true,
        suggestedCategoryId: null,
      },
    ],
  };
}
describe('Bank Connections review', () => {
  const result = { importedCount: 1, skippedCount: 2, failedCount: 0, totalProcessed: 3 };
  let api: {
    connection: ReturnType<typeof vi.fn>;
    connect: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    sync: ReturnType<typeof vi.fn>;
    columns: ReturnType<typeof vi.fn>;
    preview: ReturnType<typeof vi.fn>;
    confirm: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    api = {
      connection: vi.fn(() => of(demo)),
      connect: vi.fn(() => of({ ...demo, connected: true })),
      disconnect: vi.fn(() => of(demo)),
      sync: vi.fn(() => of(result)),
      columns: vi.fn(() => of(['Date', 'Description', 'Amount'])),
      preview: vi.fn(() => of(statement())),
      confirm: vi.fn(() => of(result)),
      cancel: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      imports: [BankConnections],
      providers: [
        { provide: BankService, useValue: api },
        {
          provide: CategoryService,
          useValue: {
            list: () =>
              of([
                { id: 7, name: 'Food', type: 'EXPENSE' },
                { id: 8, name: 'Salary', type: 'INCOME' },
              ]),
          },
        },
      ],
    });
  });
  afterEach(() => TestBed.resetTestingModule());
  function create() {
    const fixture = TestBed.createComponent(BankConnections);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance };
  }
  function review(page: BankConnections) {
    page.file = new File(['fictional'], 'demo.csv');
    page.dateConfirmed = true;
    page.buildPreview();
  }
  it('labels demo data and connects and disconnects without credentials', () => {
    const { page, fixture } = create();
    expect(fixture.nativeElement.textContent).toContain('SIMULATION');
    expect(fixture.nativeElement.querySelector('input[type=password]')).toBeNull();
    page.connect();
    expect(page.connection()?.connected).toBe(true);
    page.disconnect();
    expect(page.connection()?.connected).toBe(false);
  });
  it('requires confirmed date order before creating a preview', () => {
    const { page } = create();
    page.file = new File(['demo'], 'demo.csv');
    page.buildPreview();
    expect(api.preview).not.toHaveBeenCalled();
    page.dateConfirmed = true;
    page.buildPreview();
    expect(api.preview).toHaveBeenCalledOnce();
    expect(api.confirm).not.toHaveBeenCalled();
  });
  it('excludes invalid and duplicate rows and requires a compatible category', () => {
    const { page } = create();
    review(page);
    const rows = page.preview()!.rows;
    expect(rows.map((row) => row.include)).toEqual([true, false, false]);
    expect(rows[0].categoryId).toBeNull();
    expect(rows[0].suggestedCategoryId).toBe(7);
    expect(page.ready()).toBe(false);
    expect(page.choices(rows[0]).map((category) => category.id)).toEqual([7]);
    rows[0].categoryId = 7;
    expect(page.ready()).toBe(true);
  });
  it('renders uploaded markup as text and cancels without confirmation', () => {
    const { page, fixture } = create();
    review(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('td.description img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('<img src=x onerror=alert(1)>');
    page.cancel();
    expect(api.cancel).toHaveBeenCalledWith('test-preview');
    expect(api.confirm).not.toHaveBeenCalled();
    expect(page.preview()).toBeNull();
    expect(page.file).toBeNull();
  });
  it('confirms only after category review and displays result', () => {
    const { page } = create();
    review(page);
    page.confirm();
    expect(api.confirm).not.toHaveBeenCalled();
    page.preview()!.rows[0].categoryId = 7;
    page.confirm();
    expect(api.confirm).toHaveBeenCalledOnce();
    expect(page.preview()).toBeNull();
    expect(page.result()).toEqual(result);
  });
  it('normalizes debit credit mappings and reports server errors', () => {
    const { page } = create();
    page.mode = 'debit-credit';
    page.mapping['debit'] = 2;
    page.mapping['credit'] = 3;
    review(page);
    expect(api.preview.mock.calls[0][1]['amount']).toBe(-1);
    api.cancel.mockReturnValue(throwError(() => ({ error: { message: 'Preview expired' } })));
    page.cancel();
    expect(page.error()).toBe('Preview expired');
    expect(page.busy()).toBe(false);
  });
  it('rejects oversized files before upload', () => {
    const { page } = create();
    page.selectFile({
      target: { files: [new File([new Uint8Array(1024 * 1024 + 1)], 'large.csv')] },
    } as unknown as Event);
    expect(api.columns).not.toHaveBeenCalled();
    expect(page.error()).toContain('1 MiB');
  });
});
