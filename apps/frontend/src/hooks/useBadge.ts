import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { BadgeResponse } from '../types/api';
import { useAuthStore } from '../store/authStore';

export function useBadge() {
  const isAuthed = useAuthStore((s) => !!s.accessToken);
  return useQuery({
    queryKey: ['badge'],
    queryFn: () => api.get<BadgeResponse>('/api/histories/badge'),
    refetchInterval: 30_000,
    enabled: isAuthed,
  });
}
