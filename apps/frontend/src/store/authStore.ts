import { create } from 'zustand';
import type { UserResponse } from '../types/api';

const STORAGE_KEY = 'raditomo.auth';

interface PersistedAuth {
  accessToken: string;
  user: UserResponse;
}

function load(): PersistedAuth | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as PersistedAuth) : null;
  } catch {
    return null;
  }
}

function persist(value: PersistedAuth | null) {
  if (value) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } else {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}

interface AuthState {
  accessToken: string | null;
  user: UserResponse | null;
  setAuth: (token: string, user: UserResponse) => void;
  clear: () => void;
}

const initial = load();

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: initial?.accessToken ?? null,
  user: initial?.user ?? null,
  setAuth: (accessToken, user) => {
    persist({ accessToken, user });
    set({ accessToken, user });
  },
  clear: () => {
    persist(null);
    set({ accessToken: null, user: null });
  },
}));

/** zustand 外（fetch ラッパーなど）からトークンを参照したいとき用。 */
export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken;
}
