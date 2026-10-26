import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  afterEveryRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { Observable, of, Subscription, switchMap, tap } from 'rxjs';
import { AiChatService } from '../../../core/services/ai-chat';
import { AuthService } from '../../../core/services/auth';
import {
  ChatMessage,
  ChatMode,
  Conversation,
  ConversationDetails,
} from '../../../core/models/chat.model';

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [MatIconModule, FormsModule, DatePipe],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.scss',
})
export class AiAssistant {
  private readonly api = inject(AiChatService);
  private readonly auth = inject(AuthService);
  private owner = this.auth.currentUser();
  private requests = new Subscription();
  private operation = new Subscription();
  private listRequest = new Subscription();
  private generation = 0;
  private listGeneration = 0;
  private historyPage = 0;
  // Preserve the write guard even when another conversation is selected.
  private readonly uncertain = new Map<string, { question: string; before: Set<string> }>();
  private readonly messageArea = viewChild<ElementRef<HTMLElement>>('messageArea');
  private scrollKey = '';

  readonly isOpen = signal(false);
  readonly showHistory = signal(false);
  readonly mode = signal<ChatMode>('FINANCE');
  readonly selected = signal<Conversation | null>(null);
  readonly conversations = signal<Conversation[]>([]);
  readonly messages = signal<ChatMessage[]>([]);
  readonly draft = signal('');
  readonly sending = signal(false);
  readonly loading = signal(false);
  readonly loadingHistory = signal(false);
  readonly hasMore = signal(false);
  readonly error = signal('');
  readonly historyError = signal('');
  readonly disclaimer = signal('');
  readonly needsReconciliation = signal(false);
  readonly historyReady = signal(true);
  readonly period = signal(this.currentPeriod());
  readonly busy = computed(() => this.sending() || this.loading());
  readonly canSend = computed(
    () =>
      !this.busy() &&
      !this.needsReconciliation() &&
      this.historyReady() &&
      !!this.draft().trim() &&
      this.draft().trim().length <= 1000 &&
      !!this.auth.currentUser() &&
      (this.mode() === 'GENERAL' || !!this.selected() || this.validPeriod()),
  );

  constructor() {
    this.auth.sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.resetSession(this.auth.currentUser());
    });
    effect(() => {
      const owner = this.auth.currentUser();
      if (owner !== this.owner) this.resetSession(owner);
    });
    afterEveryRender(() => {
      const key = `${this.isOpen()}:${this.showHistory()}:${this.selected()?.id}:${this.messages().length}:${this.sending()}`;
      if (key === this.scrollKey) return;
      this.scrollKey = key;
      const area = this.messageArea()?.nativeElement;
      if (area) area.scrollTop = area.scrollHeight;
    });
    inject(DestroyRef).onDestroy(() => {
      this.requests.unsubscribe();
      this.uncertain.clear();
    });
  }

  toggleAssistant(): void {
    if (!this.session()) return;
    this.isOpen.update((value) => !value);
  }
  closeAssistant(): void {
    this.isOpen.set(false);
  }

  newChat(mode = this.mode()): void {
    if (!this.session() || this.sending()) return;
    this.cancelOperation();
    this.mode.set(mode);
    this.selected.set(null);
    this.messages.set([]);
    this.draft.set('');
    this.error.set('');
    this.disclaimer.set('');
    this.historyReady.set(true);
    this.needsReconciliation.set(false);
    this.showHistory.set(false);
  }
  changeMode(mode: ChatMode): void {
    if (mode !== this.mode()) this.newChat(mode);
  }

  toggleHistory(): void {
    if (!this.session()) return;
    this.showHistory.update((value) => !value);
    if (this.showHistory()) this.loadConversations();
  }
  loadConversations(more = false): void {
    if (!this.session()) return;
    this.listRequest.unsubscribe();
    const revision = ++this.listGeneration;
    const owner = this.owner;
    const page = more ? this.historyPage + 1 : 0;
    this.loadingHistory.set(true);
    this.historyError.set('');
    this.listRequest = this.api.list(page).subscribe({
      next: (result) => {
        if (!this.sessionMatches(owner) || revision !== this.listGeneration) return;
        this.historyPage = page;
        this.conversations.set(more ? [...this.conversations(), ...result.items] : result.items);
        this.hasMore.set(result.items.length === result.size);
        this.loadingHistory.set(false);
      },
      error: (error) => {
        if (!this.sessionMatches(owner) || revision !== this.listGeneration) return;
        this.loadingHistory.set(false);
        this.historyError.set(this.errorText(error));
      },
    });
    this.requests.add(this.listRequest);
  }

  selectConversation(conversation: Conversation): void {
    if (!this.session() || this.sending()) return;
    this.cancelOperation();
    this.selected.set(conversation);
    this.mode.set(conversation.mode);
    this.messages.set([]);
    this.draft.set(this.uncertain.get(conversation.id)?.question ?? '');
    this.disclaimer.set('');
    this.showHistory.set(false);
    this.needsReconciliation.set(this.uncertain.has(conversation.id));
    this.refreshMessages();
  }

  refreshMessages(): void {
    if (!this.session() || this.sending() || !this.selected()) return;
    this.cancelOperation();
    this.loading.set(true);
    this.historyReady.set(false);
    this.error.set('');
    const id = this.selected()!.id;
    this.run(
      this.api.history(id),
      (details) => this.applyHistory(details),
      (error) => {
        this.loading.set(false);
        this.error.set(this.errorText(error));
      },
    );
  }

  send(): void {
    if (!this.session() || !this.canSend()) return;
    const question = this.draft().trim();
    const mode = this.mode();
    const [year, month] = this.period().split('-').map(Number);
    this.cancelOperation();
    this.sending.set(true);
    this.error.set('');
    const before = new Set(this.messages().map((message) => message.id));
    let dispatched = false;
    let confirmed = false;
    const conversation = this.selected();
    const source = conversation
      ? of(conversation)
      : this.api.create({
          mode,
          title: question.slice(0, 100),
          ...(mode === 'FINANCE' ? { month, year } : {}),
        });
    this.run(
      source.pipe(
        tap((created) => this.selected.set(created)),
        switchMap((created) => {
          dispatched = true;
          return this.api.send(created.id, { question });
        }),
        tap((response) => {
          confirmed = true;
          this.draft.set('');
          this.disclaimer.set(response.disclaimer);
          this.historyReady.set(false);
        }),
        switchMap(() => this.api.history(this.selected()!.id)),
      ),
      (details) => {
        this.sending.set(false);
        this.applyHistory(details);
      },
      (error) => {
        this.sending.set(false);
        const id = this.selected()?.id;
        if (confirmed) {
          this.error.set('Your message was saved. Refresh messages to display the response.');
          return;
        }
        const safeFailure =
          error instanceof HttpErrorResponse &&
          ([400, 401, 403, 404].includes(error.status) ||
            (error.status === 503 && error.error?.code === 'CHAT_UNAVAILABLE'));
        if (dispatched && id && !safeFailure) {
          this.uncertain.set(id, { question, before });
          this.needsReconciliation.set(true);
          this.refreshMessages();
        } else {
          this.error.set(
            this.errorText(error) +
              (!dispatched && !safeFailure
                ? ' Conversation creation may have completed. Check history before creating another.'
                : ''),
          );
        }
      },
    );
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  private applyHistory(details: ConversationDetails): void {
    this.selected.set(details);
    this.mode.set(details.mode);
    this.messages.set(details.messages);
    this.disclaimer.set(details.disclaimer);
    this.loading.set(false);
    this.historyReady.set(true);
    const uncertain = this.uncertain.get(details.id);
    if (!uncertain) return;
    const added = details.messages.filter((message) => !uncertain.before.has(message.id));
    const index = added.findIndex(
      (message) => message.role === 'user' && message.content === uncertain.question,
    );
    if (index >= 0 && added[index + 1]?.role === 'assistant') {
      this.uncertain.delete(details.id);
      this.needsReconciliation.set(false);
      this.draft.set('');
      this.error.set('The saved response was recovered from history.');
    } else {
      this.error.set(
        'Delivery is uncertain. Your draft is preserved. Refresh history again, or start a new chat. Resending here is blocked to avoid duplicates.',
      );
    }
  }

  private run<T>(
    source: Observable<T>,
    next: (value: T) => void,
    error: (error: unknown) => void,
  ): void {
    const generation = this.generation;
    const owner = this.owner;
    // Container also handles synchronous test Observables and nested reconciliation.
    const container = new Subscription();
    this.operation = container;
    this.requests.add(container);
    container.add(
      source.subscribe({
        next: (value) => {
          if (this.sessionMatches(owner) && generation === this.generation) next(value);
        },
        error: (failure) => {
          if (this.sessionMatches(owner) && generation === this.generation) error(failure);
        },
      }),
    );
  }
  private cancelOperation(): void {
    this.generation++;
    this.operation.unsubscribe();
    this.loading.set(false);
  }
  private session(): boolean {
    const owner = this.auth.currentUser();
    if (owner !== this.owner) this.resetSession(owner);
    return !!owner;
  }
  private sessionMatches(owner: ReturnType<AuthService['currentUser']>): boolean {
    return this.auth.currentUser() === owner && this.owner === owner;
  }
  private resetSession(owner: ReturnType<AuthService['currentUser']>): void {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.generation++;
    this.listGeneration++;
    this.owner = owner;
    this.uncertain.clear();
    this.isOpen.set(false);
    this.showHistory.set(false);
    this.selected.set(null);
    this.conversations.set([]);
    this.messages.set([]);
    this.draft.set('');
    this.mode.set('FINANCE');
    this.period.set(this.currentPeriod());
    this.sending.set(false);
    this.loading.set(false);
    this.loadingHistory.set(false);
    this.hasMore.set(false);
    this.needsReconciliation.set(false);
    this.historyReady.set(true);
    this.error.set('');
    this.historyError.set('');
    this.disclaimer.set('');
  }
  private currentPeriod(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }
  private validPeriod(): boolean {
    return /^(20\d{2}|2100)-(0[1-9]|1[0-2])$/.test(this.period());
  }
  private errorText(error: unknown): string {
    if (!(error instanceof HttpErrorResponse))
      return 'The assistant is unavailable. Please try again later.';
    if (error.status === 401) return 'Your session expired. Please sign in again.';
    if (error.status === 403) return 'You do not have access to this conversation.';
    if (error.status === 400) return 'Invalid request. Check your message and financial month.';
    if (error.status === 404) return 'Conversation not found. Choose another chat.';
    if (error.status === 503 && error.error?.code === 'CHAT_UNAVAILABLE')
      return 'General AI is temporarily unavailable. Your message was not saved. You can try again.';
    if (error.status === 503) return 'Conversation storage is unavailable. Please refresh later.';
    if (error.status === 504) return 'The assistant timed out. Please refresh history.';
    return 'Unable to reach the assistant. Check your connection and try again.';
  }
}
