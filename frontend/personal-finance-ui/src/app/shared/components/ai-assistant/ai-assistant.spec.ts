import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { authInterceptor } from '../../../core/interceptors/auth.interceptor';
import { testCredentials, testLogin } from '../../../core/auth.testing';
import { ChatMessage, Conversation, ConversationDetails } from '../../../core/models/chat.model';
import { AiAssistant } from './ai-assistant';

const base = '/api/v1/chat/conversations';
const conversation: Conversation = {
  id: '0123456789abcdef01234567',
  title: 'Budget questions',
  mode: 'FINANCE',
  month: 10,
  year: 2026,
  createdAt: '2026-10-26T01:00:00Z',
  updatedAt: '2026-10-26T01:00:00Z',
};
const general: Conversation = {
  ...conversation,
  id: 'abcdef0123456789abcdef01',
  mode: 'GENERAL',
  month: undefined,
  year: undefined,
};
const messages: ChatMessage[] = [
  {
    id: '111111111111111111111111',
    conversationId: conversation.id,
    role: 'user',
    provider: 'user',
    content: 'How can I save?',
    createdAt: conversation.createdAt,
  },
  {
    id: '222222222222222222222222',
    conversationId: conversation.id,
    role: 'assistant',
    provider: 'rule-based',
    content: 'Review your monthly budget.',
    createdAt: conversation.createdAt,
  },
];
const details = (c = conversation, saved = messages): ConversationDetails => ({
  ...c,
  messages: saved,
  page: 0,
  size: 100,
  disclaimer: 'Educational information only.',
});

describe('AI Assistant authenticated conversations', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<AiAssistant>>;
  let chat: AiAssistant;
  let auth: AuthService;
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AiAssistant],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth = TestBed.inject(AuthService);
    auth.login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    fixture = TestBed.createComponent(AiAssistant);
    chat = fixture.componentInstance;
    await fixture.whenStable();
  });
  afterEach(() => http.verify());
  function history(c = conversation, saved = messages) {
    const request = http.expectOne(`${base}/${c.id}?page=0&size=100`);
    request.flush(details(c, saved));
    return request;
  }
  function select(c = conversation, saved = messages) {
    chat.selectConversation(c);
    history(c, saved);
  }
  function beginSend() {
    select(conversation, []);
    chat.draft.set('How can I save?');
    chat.send();
    return http.expectOne(`${base}/${conversation.id}/messages`);
  }

  it('opens, closes and preserves the session draft', async () => {
    fixture.nativeElement.querySelector('[aria-label="Open AI Assistant"]').click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.chat-panel')).toBeTruthy();
    chat.draft.set('Draft question');
    chat.closeAssistant();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.chat-panel')).toBeNull();
    chat.toggleAssistant();
    expect(chat.draft()).toBe('Draft question');
  });
  it.each(['GENERAL', 'FINANCE'] as const)(
    'creates and sends a real %s conversation',
    async (mode) => {
      chat.toggleAssistant();
      chat.changeMode(mode);
      chat.period.set('2026-10');
      chat.draft.set('How can I save?');
      chat.send();
      expect(chat.sending()).toBe(true);
      const creation = http.expectOne(base);
      expect(creation.request.headers.get('Authorization')).toBe('Bearer test-token-only');
      expect(creation.request.body).toEqual({
        mode,
        title: 'How can I save?',
        ...(mode === 'FINANCE' ? { month: 10, year: 2026 } : {}),
      });
      const c = mode === 'FINANCE' ? conversation : general;
      creation.flush(c);
      const send = http.expectOne(`${base}/${c.id}/messages`);
      expect(send.request.body).toEqual({ question: 'How can I save?' });
      expect(chat.messages()).toEqual([]);
      send.flush({
        answer: 'Review your monthly budget.',
        provider: 'rule-based',
        disclaimer: 'Educational information only.',
      });
      expect(chat.draft()).toBe('');
      history(c);
      await fixture.whenStable();
      expect(chat.sending()).toBe(false);
      expect(fixture.nativeElement.querySelector('.message.assistant').textContent).toContain(
        'Review your monthly budget.',
      );
      expect(fixture.nativeElement.querySelectorAll('time').length).toBe(2);
    },
  );
  it('loads empty and populated history with backend ordering and metadata', async () => {
    chat.toggleAssistant();
    chat.toggleHistory();
    expect(chat.loadingHistory()).toBe(true);
    http.expectOne(`${base}?page=0&size=20`).flush({ items: [], page: 0, size: 20 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No conversations yet');
    chat.loadConversations();
    http
      .expectOne(`${base}?page=0&size=20`)
      .flush({ items: [general, conversation], page: 0, size: 20 });
    expect(chat.conversations()).toEqual([general, conversation]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.conversation').textContent).toContain('GENERAL');
  });
  it('resumes an existing conversation without creating a replacement', () => {
    select();
    expect(chat.messages()).toEqual(messages);
    chat.draft.set('Explain my expenses');
    chat.send();
    http
      .expectOne(`${base}/${conversation.id}/messages`)
      .flush({ answer: 'Saved answer', provider: 'rule-based', disclaimer: 'Educational' });
    history();
    expect(chat.selected()?.id).toBe(conversation.id);
    http.expectNone(base);
  });
  it('new chat and mode changes preserve the mode of persisted chats', () => {
    select();
    chat.changeMode('GENERAL');
    expect(chat.selected()).toBeNull();
    expect(chat.messages()).toEqual([]);
    expect(chat.mode()).toBe('GENERAL');
    select(conversation);
    expect(chat.mode()).toBe('FINANCE');
    chat.newChat();
    expect(chat.selected()).toBeNull();
    expect(chat.mode()).toBe('FINANCE');
  });
  it('prevents rapid duplicate sends and switching during a write', () => {
    const request = beginSend();
    chat.send();
    chat.newChat();
    chat.changeMode('GENERAL');
    chat.selectConversation(general);
    expect(chat.selected()?.id).toBe(conversation.id);
    expect(chat.mode()).toBe('FINANCE');
    request.flush({ answer: 'Answer', provider: 'rule-based', disclaimer: 'Educational' });
    history();
  });
  it('cancels an older history read when selection changes', () => {
    chat.selectConversation(conversation);
    const old = http.expectOne(`${base}/${conversation.id}?page=0&size=100`);
    chat.selectConversation(general);
    expect(old.cancelled).toBe(true);
    history(general, []);
    expect(chat.selected()?.id).toBe(general.id);
    expect(chat.messages()).toEqual([]);
  });
  it('blocks sends until history loads and allows retrying failed reads', () => {
    chat.selectConversation(conversation);
    chat.draft.set('Hello');
    chat.send();
    http.expectNone(`${base}/${conversation.id}/messages`);
    http
      .expectOne(`${base}/${conversation.id}?page=0&size=100`)
      .flush({}, { status: 502, statusText: 'Bad Gateway' });
    expect(chat.historyReady()).toBe(false);
    chat.refreshMessages();
    history();
    expect(chat.historyReady()).toBe(true);
  });
  it.each([400, 403, 404])('preserves the draft after a definite %s failure', (status) => {
    const request = beginSend();
    request.flush({ message: 'INTERNAL SECRET' }, { status, statusText: 'Failure' });
    expect(chat.draft()).toBe('How can I save?');
    expect(chat.error()).not.toContain('INTERNAL SECRET');
    expect(chat.needsReconciliation()).toBe(false);
  });
  it('allows a deliberate retry when the provider confirms no saved turn', () => {
    beginSend().flush({ code: 'CHAT_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    expect(chat.draft()).toBe('How can I save?');
    expect(chat.canSend()).toBe(true);
    chat.send();
    http
      .expectOne(`${base}/${conversation.id}/messages`)
      .flush({ answer: 'Answer', provider: 'openai', disclaimer: 'Verify' });
    history();
  });
  it.each([0, 502, 503, 504])(
    'reconciles ambiguous %s sends without retrying the POST',
    (status) => {
      beginSend().flush({ code: 'PERSISTENCE_UNAVAILABLE' }, { status, statusText: 'Unavailable' });
      expect(chat.loading()).toBe(true);
      history(conversation, []);
      expect(chat.draft()).toBe('How can I save?');
      expect(chat.needsReconciliation()).toBe(true);
      chat.send();
      http.expectNone(`${base}/${conversation.id}/messages`);
      chat.selectConversation(general);
      history(general, []);
      select(conversation, []);
      expect(chat.needsReconciliation()).toBe(true);
      chat.refreshMessages();
      history();
      expect(chat.needsReconciliation()).toBe(false);
      expect(chat.draft()).toBe('');
    },
  );
  it('keeps the write guard when MongoDB also prevents reconciliation', () => {
    beginSend().flush({}, { status: 504, statusText: 'Timeout' });
    http
      .expectOne(`${base}/${conversation.id}?page=0&size=100`)
      .flush({ code: 'PERSISTENCE_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    expect(chat.error()).toContain('storage');
    expect(chat.needsReconciliation()).toBe(true);
    expect(chat.draft()).toBe('How can I save?');
  });
  it('does not resubmit a confirmed saved turn when its history reload fails', () => {
    beginSend().flush({ answer: 'Answer', provider: 'rule-based', disclaimer: 'Educational' });
    http
      .expectOne(`${base}/${conversation.id}?page=0&size=100`)
      .flush({}, { status: 502, statusText: 'Unavailable' });
    expect(chat.draft()).toBe('');
    expect(chat.error()).toContain('was saved');
    chat.draft.set('Next question');
    chat.send();
    http.expectNone(`${base}/${conversation.id}/messages`);
    chat.refreshMessages();
    history();
    expect(chat.canSend()).toBe(true);
  });
  it('reports history and creation failures safely and preserves unsent text', () => {
    chat.loadConversations();
    http
      .expectOne(`${base}?page=0&size=20`)
      .flush({ message: 'secret stack trace' }, { status: 503, statusText: 'Unavailable' });
    expect(chat.historyError()).toContain('storage');
    chat.draft.set('Unsent text');
    chat.send();
    http.expectOne(base).flush({}, { status: 504, statusText: 'Timeout' });
    expect(chat.draft()).toBe('Unsent text');
    expect(chat.error()).toContain('Check history');
  });
  it('clears state and cancels requests on logout; a new user sees no old content', async () => {
    const request = beginSend();
    chat.toggleAssistant();
    chat.loadConversations();
    const list = http.expectOne(`${base}?page=0&size=20`);
    auth.logout();
    expect(request.cancelled).toBe(true);
    expect(chat.draft()).toBe('');
    await fixture.whenStable();
    expect(request.cancelled).toBe(true);
    expect(list.cancelled).toBe(true);
    expect(chat.isOpen()).toBe(false);
    expect(chat.messages()).toEqual([]);
    expect(chat.selected()).toBeNull();
    expect(chat.draft()).toBe('');
    auth.login(testCredentials).subscribe();
    http
      .expectOne('/api/v1/auth/login')
      .flush({ ...testLogin, user: { ...testLogin.user, id: 'another-user' } });
    await fixture.whenStable();
    chat.toggleAssistant();
    expect(chat.messages()).toEqual([]);
    expect(chat.conversations()).toEqual([]);
  });
  it('handles an expired JWT through the existing interceptor and clears private state', async () => {
    beginSend().flush({}, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(auth.currentUser()).toBeNull();
    expect(chat.messages()).toEqual([]);
    expect(chat.draft()).toBe('');
    expect(chat.selected()).toBeNull();
  });
  it('clears private state across same-user relogin before a render occurs', () => {
    beginSend();
    auth.logout();
    auth.login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush(testLogin);
    expect(chat.messages()).toEqual([]);
    expect(chat.selected()).toBeNull();
    expect(chat.draft()).toBe('');
    expect(chat.sending()).toBe(false);
  });
  it('uses the stored finance period when reopening despite an invalid new-chat period', () => {
    chat.period.set('1999-01');
    select();
    chat.draft.set('Explain my budget');
    expect(chat.canSend()).toBe(true);
  });
  it('rejects blank, overlong messages and invalid finance periods', () => {
    chat.draft.set('  ');
    chat.send();
    chat.draft.set('a'.repeat(1001));
    chat.send();
    chat.draft.set('Hello');
    chat.period.set('1999-01');
    chat.send();
    http.expectNone(base);
  });
  it('Enter sends, Shift+Enter and IME composition preserve input', () => {
    select(conversation, []);
    chat.draft.set('Hello');
    chat.onKeydown(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    chat.onKeydown(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true }));
    http.expectNone(`${base}/${conversation.id}/messages`);
    chat.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));
    http
      .expectOne(`${base}/${conversation.id}/messages`)
      .flush({ answer: 'Answer', provider: 'rule-based', disclaimer: 'Educational' });
    history();
  });
  it('renders chat content as text, including apparent HTML', async () => {
    chat.toggleAssistant();
    select(conversation, [
      { ...messages[1], content: '<img src=x onerror="alert(1)"><script>bad()</script>' },
    ]);
    await fixture.whenStable();
    const bubble = fixture.nativeElement.querySelector('.message');
    expect(bubble.textContent).toContain('<script>bad()</script>');
    expect(bubble.querySelector('img, script')).toBeNull();
  });
});
