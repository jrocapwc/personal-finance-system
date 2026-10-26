import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { BankConnections } from './bank-connections';
import { BankService, DemoConnection, StatementPreview } from '../../core/services/bank';
import { CategoryService } from '../../core/services/category';
const demo: DemoConnection = {
  providerId: 'metrobank',
  provider: 'Metrobank',
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
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Demo connected to Metrobank"]').disabled).toBe(true);
    fixture.nativeElement.querySelector('[aria-label="Hide balance"]').click();
    fixture.detectChanges();
    const balance = fixture.nativeElement.querySelector('.balance-value');
    expect(balance.textContent).not.toContain(demo.demoBalance);
    expect(balance.querySelector('strong').textContent).toContain('PHP');
    fixture.nativeElement.querySelector('[aria-label="Show balance"]').click();
    fixture.detectChanges();
    expect(balance.textContent).toContain(demo.demoBalance);
    page.disconnect();
    expect(page.connection()?.connected).toBe(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Connect to Metrobank"]').disabled).toBe(true);
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
  it('keeps every provider placeholder disabled without opening files or changing state', () => {
    const { page, fixture } = create();
    expect(fixture.nativeElement.querySelectorAll('.bank-card')).toHaveLength(6);
    expect(fixture.nativeElement.querySelectorAll('.bank-card img')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('.bank-card:last-child mat-icon').getAttribute('svgIcon')).toBe('account_balance');
    expect(page.providerLogo('bpi')).toBe('banks/bpi.svg');
    expect(page.providerLogo('bdo')).toBe('banks/bdo.svg');
    expect(page.providerLogo('unknown-provider')).toBeUndefined();
    const picker = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    const browse = vi.spyOn(picker, 'click').mockImplementation(() => {});
    const state = page.banks.map((bank) => ({ ...bank }));
    for (const button of fixture.nativeElement.querySelectorAll('.bank-connect-button')) {
      expect(button.disabled).toBe(true);
      expect(button.type).toBe('button');
      expect(button.textContent.trim()).toBe('Connect');
      button.click();
      button.click();
    }
    expect(page.banks).toEqual(state);
    expect(page.banks.every((bank) => !page.isDemoConnected(bank.id))).toBe(true);
    expect(browse).not.toHaveBeenCalled();
    expect(api.connect).not.toHaveBeenCalled();
    expect(api.sync).not.toHaveBeenCalled();
    expect(api.columns).not.toHaveBeenCalled();
    expect(api.preview).not.toHaveBeenCalled();
    expect(api.confirm).not.toHaveBeenCalled();
  });
  it('restores a connected provider from persisted API state using its stable ID', () => {
    api.connection.mockReturnValue(of({ ...demo, provider: 'Renamed demo provider', connected: true }));
    const { fixture } = create();
    const button = fixture.nativeElement.querySelector('[aria-label="Demo connected to Metrobank"]');
    expect(button.textContent.trim()).toBe('Connected');
    expect(button.disabled).toBe(true);
    expect(button.classList.contains('connected')).toBe(true);
    expect(button.querySelector('mat-icon')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('DEMO CONNECTED');
    button.click();
    expect(api.connect).not.toHaveBeenCalled();
    expect(api.columns).not.toHaveBeenCalled();
  });
  it('derives each provider status only from matching simulated API state', () => {
    const { page } = create();
    // Future-provider fixtures exercise ID matching, not additional backend support.
    for (const bank of page.banks) {
      page.connection.set({ ...demo, providerId: bank.id, connected: true });
      expect(page.banks.filter((bank) => page.isDemoConnected(bank.id)).map((bank) => bank.id)).toEqual([bank.id]);
    }
    page.connection.set({ ...demo, providerId: 'unknown', connected: true });
    expect(page.banks.some((bank) => page.isDemoConnected(bank.id))).toBe(false);
    page.connection.set({ ...demo, connected: true, simulated: false });
    expect(page.isDemoConnected('metrobank')).toBe(false);
  });
  it('does not change connected state after failed connect or disconnect requests', () => {
    const { page } = create();
    api.connect.mockReturnValue(throwError(() => ({ error: { message: 'Connect failed' } })));
    page.connect();
    expect(page.isDemoConnected('metrobank')).toBe(false);
    expect(page.error()).toBe('Connect failed');
    page.connection.set({ ...demo, connected: true });
    api.disconnect.mockReturnValue(throwError(() => ({ error: { message: 'Disconnect failed' } })));
    page.disconnect();
    expect(page.isDemoConnected('metrobank')).toBe(true);
    expect(page.error()).toBe('Disconnect failed');
  });
  it('serializes sync refresh with mutations and handles refresh failure', () => {
    const { page } = create();
    page.connect();
    const refresh = new Subject<DemoConnection>();
    api.connection.mockReturnValue(refresh);
    page.sync();
    expect(page.busy()).toBe(true);
    page.disconnect();
    expect(api.disconnect).not.toHaveBeenCalled();
    refresh.next({ ...demo, connected: true });
    refresh.complete();
    expect(page.busy()).toBe(false);
    page.disconnect();
    expect(page.isDemoConnected('metrobank')).toBe(false);
    api.connection.mockReturnValue(throwError(() => ({ error: { message: 'Refresh failed' } })));
    page.sync();
    expect(page.error()).toBe('Refresh failed');
    expect(page.busy()).toBe(false);
    expect(page.isDemoConnected('metrobank')).toBe(false);
  });
  it('opens the CSV picker only from dedicated upload controls', () => {
    const { fixture } = create();
    const picker = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    const browse = vi.spyOn(picker, 'click').mockImplementation(() => {});
    fixture.nativeElement.querySelector('.format-card button').click();
    fixture.nativeElement.querySelector('.drop-zone').click();
    expect(browse).toHaveBeenCalledTimes(2);
    expect(api.connect).not.toHaveBeenCalled();
    expect(api.confirm).not.toHaveBeenCalled();
  });
  it('accepts a dropped statement through the existing validation flow without importing it', () => {
    const { page } = create();
    const file = new File(
      ['Date,Description,Amount\n2026-10-01,Fictional Food,-12.34'],
      'fictional.csv',
    );
    const event = {
      preventDefault: vi.fn(),
      dataTransfer: { files: [file] },
    } as unknown as DragEvent;
    page.dragOver(event);
    expect(page.dragging()).toBe(true);
    page.dropFile(event);
    expect(event.preventDefault).toHaveBeenCalled();
    expect(page.dragging()).toBe(false);
    expect(page.file).toBe(file);
    expect(api.columns).toHaveBeenCalledWith(file);
    expect(page.headers()).toEqual(['Date', 'Description', 'Amount']);
    expect(api.preview).not.toHaveBeenCalled();
    expect(api.confirm).not.toHaveBeenCalled();
  });
  it('rejects multiple dropped files and prevents replacement during work or review', () => {
    const { page } = create();
    const file = new File(['fictional'], 'fictional.csv');
    const event = (files: File[]) =>
      ({ preventDefault: vi.fn(), dataTransfer: { files } }) as unknown as DragEvent;
    page.dropFile(event([file, file]));
    expect(page.error()).toContain('one CSV statement');
    expect(api.columns).not.toHaveBeenCalled();
    page.busy.set(true);
    page.dropFile(event([file]));
    expect(api.columns).not.toHaveBeenCalled();
    page.busy.set(false);
    review(page);
    page.dropFile(event([file]));
    expect(api.columns).not.toHaveBeenCalled();
    expect(page.preview()?.id).toBe('test-preview');
  });
});
