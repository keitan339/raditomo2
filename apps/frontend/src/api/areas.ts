import { api } from './client';
import type { AreaResponse, StationResponse } from '../types/api';

export const areasApi = {
  list: () => api.get<AreaResponse[]>('/api/areas'),
  stations: (areaId: string) =>
    api.get<StationResponse[]>(`/api/stations?areaId=${encodeURIComponent(areaId)}`),
};
