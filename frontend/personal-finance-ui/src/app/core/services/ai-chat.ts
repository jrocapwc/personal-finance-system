import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';
import {
  ChatMessageRequest,
  ChatResponse,
  Conversation,
  ConversationDetails,
  ConversationPage,
  ConversationRequest,
} from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/v1/chat/conversations';
  create({ mode, title, month, year }: ConversationRequest): Observable<Conversation> {
    return this.http.post<Conversation>(this.url, {
      mode,
      ...(title !== undefined ? { title } : {}),
      ...(mode === 'FINANCE' ? { month, year } : {}),
    });
  }
  list(page = 0): Observable<ConversationPage> {
    return this.http.get<ConversationPage>(this.url, {
      params: { page, size: 20 },
      transferCache: false,
    });
  }
  get(id: string, page = 0): Observable<ConversationDetails> {
    return this.http.get<ConversationDetails>(`${this.url}/${this.id(id)}`, {
      params: { page, size: 100 },
      transferCache: false,
    });
  }
  // History is embedded in GET /{id}; there is no separate GET /messages endpoint.
  history(id: string): Observable<ConversationDetails> {
    return this.get(id).pipe(
      expand((result) =>
        result.messages.length === result.size ? this.get(id, result.page + 1) : EMPTY,
      ),
      reduce(
        (all: ConversationDetails | null, page) =>
          all ? { ...page, messages: [...all.messages, ...page.messages] } : page,
        null,
      ),
      map((result) => result!),
    );
  }
  send(id: string, { question }: ChatMessageRequest): Observable<ChatResponse> {
    // No retry: the API does not provide idempotency keys.
    return this.http.post<ChatResponse>(`${this.url}/${this.id(id)}/messages`, { question });
  }
  private id(id: string): string {
    if (!/^[a-f\d]{24}$/i.test(id)) throw new Error('Invalid conversation ID');
    return id;
  }
}
