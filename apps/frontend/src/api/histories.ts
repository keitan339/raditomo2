import { api } from './client';
import type { BadgeResponse, HistoryItem, PageResponse } from '../types/api';
import type { DownloadStatus } from '../types/api';

export const historiesApi = {
  list: (status: DownloadStatus | null, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return api.get<PageResponse<HistoryItem>>(`/api/histories?${params.toString()}`);
  },
  delete: (id: number) => api.del<void>(`/api/histories/${id}`),
  markSeen: () => api.post<BadgeResponse>('/api/histories/mark-seen'),
};
