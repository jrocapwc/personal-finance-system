import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiChatService } from './ai-chat';

describe('AiChatService contracts', () => {
  const base = '/api/v1/chat/conversations';
  const id = '0123456789abcdef01234567';
  let api: AiChatService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AiChatService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('retrieves all chronological message pages, including an empty final page', () => {
    const rows = Array.from({ length: 100 }, (_, i) => ({
      id: String(i),
      content: `Message ${i}`,
    }));
    let result: unknown;
    api.history(id).subscribe((value) => (result = value.messages));
    const first = http.expectOne(`${base}/${id}?page=0&size=100`);
    expect(first.request.transferCache).toBe(false);
    first.flush({ id, messages: rows, page: 0, size: 100 });
    http.expectOne(`${base}/${id}?page=1&size=100`).flush({ id, messages: [], page: 1, size: 100 });
    expect(result).toEqual(rows);
  });
  it('uses actual list pagination and disables private SSR transfer caching', () => {
    api.list(2).subscribe();
    const request = http.expectOne(`${base}?page=2&size=20`);
    expect(request.request.transferCache).toBe(false);
    request.flush({ items: [], page: 2, size: 20 });
  });
  it('sends only established request fields and never user identity', () => {
    api.create({ mode: 'GENERAL', month: 10, year: 2026 }).subscribe();
    const create = http.expectOne(base);
    expect(create.request.body).toEqual({ mode: 'GENERAL' });
    create.flush({ id });
    api.send(id, { question: 'Hello' }).subscribe();
    const send = http.expectOne(`${base}/${id}/messages`);
    expect(send.request.body).toEqual({ question: 'Hello' });
    send.flush({ answer: 'Hi' });
  });
  it('rejects invalid identifiers before constructing URLs', () => {
    expect(() => api.get('../another-user')).toThrow('Invalid conversation ID');
    expect(() => api.send('invalid', { question: 'Hi' })).toThrow('Invalid conversation ID');
  });
  it('does not automatically retry failed writes', () => {
    let failed = false;
    api.send(id, { question: 'Hi' }).subscribe({ error: () => (failed = true) });
    http.expectOne(`${base}/${id}/messages`).flush({}, { status: 504, statusText: 'Timeout' });
    http.expectNone(`${base}/${id}/messages`);
    expect(failed).toBe(true);
  });
});
