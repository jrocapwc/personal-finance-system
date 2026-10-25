import { LoginResponse, UserResponse } from './models/auth.model';
export const testUser: UserResponse = {
  id: '00000000-0000-4000-8000-000000000001',
  name: 'Test User',
  email: 'test@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
export const testLogin: LoginResponse = {
  accessToken: 'test-token-only',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: testUser,
};
export const testCredentials = { email: testUser.email, password: 'TestPassword123!' };
