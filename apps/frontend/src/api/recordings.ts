import { api } from './client';
import type {
  PlaybackPositionResponse,
  RecordingGroupResponse,
  RecordingResponse,
} from '../types/api';

export const recordingsApi = {
  groups: () => api.get<RecordingGroupResponse[]>('/api/recordings/groups'),
  byTitle: (title: string) =>
    api.get<RecordingResponse[]>(
      `/api/recordings?title=${encodeURIComponent(title)}`,
    ),
  detail: (historyId: number) =>
    api.get<RecordingResponse>(`/api/recordings/${historyId}`),
  delete: (historyId: number) =>
    api.del<void>(`/api/recordings/${historyId}`),
  getPosition: (historyId: number) =>
    api.get<PlaybackPositionResponse>(
      `/api/recordings/${historyId}/playback-position`,
    ),
  putPosition: (historyId: number, positionSeconds: number) =>
    api.put<PlaybackPositionResponse>(
      `/api/recordings/${historyId}/playback-position`,
      { positionSeconds },
    ),
};
