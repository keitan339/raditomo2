import { api } from './client';
import type { LoginResponse, LoginUrlResponse, UserResponse } from '../types/api';

export const authApi = {
  loginUrl: () => api.get<LoginUrlResponse>('/api/auth/google/login-url', { skipAuth: true }),
  callback: (code: string, state: string) =>
    api.get<LoginResponse>(
      `/api/auth/google/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`,
      { skipAuth: true },
    ),
  logout: () => api.post<void>('/api/auth/logout'),
  me: () => api.get<UserResponse>('/api/auth/me'),
};
