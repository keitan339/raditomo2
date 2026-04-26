import { getAccessToken, useAuthStore } from '../store/authStore';

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: unknown) {
    super(message);
    this.name = 'ApiError';
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  skipAuth?: boolean;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getAccessToken();
  if (!options.skipAuth && token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, {
    ...options,
    headers,
    body:
      options.body === undefined
        ? undefined
        : typeof options.body === 'string'
          ? options.body
          : JSON.stringify(options.body),
  });

  if (response.status === 401 || response.status === 403) {
    // 認証切れ / 失効。明示的にクリアして UI 側で /login へ誘導する。
    if (!options.skipAuth) {
      useAuthStore.getState().clear();
    }
  }

  if (!response.ok) {
    let body: unknown;
    try {
      body = await response.json();
    } catch {
      try {
        body = await response.text();
      } catch {
        body = undefined;
      }
    }
    throw new ApiError(response.status, `HTTP ${response.status} for ${path}`, body);
  }

  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return (await response.json()) as T;
  }
  return (await response.text()) as unknown as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) =>
    apiFetch<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(path, { ...options, method: 'PUT', body }),
  del: <T = void>(path: string, options?: RequestOptions) =>
    apiFetch<T>(path, { ...options, method: 'DELETE' }),
};
