import { CategoryType } from './category.model';
export interface TransactionResponse {
  id: number;
  categoryId: number;
  type: CategoryType;
  amount: number;
  description: string | null;
  transactionDate: string;
  source: 'MANUAL' | 'MOCK_PROVIDER';
  createdAt: string;
  updatedAt: string;
}

export interface TransactionRequest {
  categoryId: number;
  type: CategoryType;
  amount: number;
  description: string | null;
  transactionDate: string;
}
export type TransactionSort =
  'id' | 'amount' | 'transactionDate' | 'createdAt' | 'updatedAt' | 'description' | 'type';
export interface TransactionQuery {
  type?: CategoryType;
  categoryId?: number;
  startDate?: string;
  endDate?: string;
  keyword?: string;
  page: number;
  size: number;
  sort: TransactionSort;
  direction: 'ASC' | 'DESC';
}
export interface TransactionPageResponse {
  content: TransactionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort: string;
  direction: 'ASC' | 'DESC';
}
