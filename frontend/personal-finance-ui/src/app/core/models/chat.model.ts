export type ChatMode = 'GENERAL' | 'FINANCE';
export interface ConversationRequest {
  mode: ChatMode;
  title?: string;
  month?: number;
  year?: number;
}
export interface Conversation {
  id: string;
  mode: ChatMode;
  title: string;
  month?: number;
  year?: number;
  createdAt: string;
  updatedAt: string;
}
export interface ChatMessage {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant';
  content: string;
  provider: 'user' | 'openai' | 'rule-based';
  createdAt: string;
}
export interface ConversationPage {
  items: Conversation[];
  page: number;
  size: number;
}
export interface ConversationDetails extends Conversation {
  messages: ChatMessage[];
  disclaimer: string;
  page: number;
  size: number;
}
export interface ChatMessageRequest {
  question: string;
}
export interface ChatResponse {
  answer: string;
  provider: 'openai' | 'rule-based';
  disclaimer: string;
}
export interface ChatApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: Record<string, string>;
}
