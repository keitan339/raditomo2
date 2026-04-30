import { api } from './client';
import type {
  StationVisibilityResponse,
  UpdateSettingsResponse,
  UserSettingsResponse,
} from '../types/api';

export const userSettingsApi = {
  get: () => api.get<UserSettingsResponse>('/api/users/me/settings'),
  update: (currentAreaId: string) =>
    api.put<UpdateSettingsResponse>('/api/users/me/settings', { currentAreaId }),
  visibility: (areaId: string) =>
    api.get<StationVisibilityResponse[]>(
      `/api/users/me/station-visibility?areaId=${encodeURIComponent(areaId)}`,
    ),
  updateVisibility: (stationId: string, visible: boolean) =>
    api.put<StationVisibilityResponse>(
      `/api/users/me/station-visibility/${encodeURIComponent(stationId)}`,
      { visible },
    ),
};
