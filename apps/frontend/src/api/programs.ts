import { api } from './client';
import type {
  ProgramDetailResponse,
  ProgramItem,
  ProgramListResponse,
} from '../types/api';

export const programsApi = {
  list: (areaId: string, date: string) =>
    api.get<ProgramListResponse>(
      `/api/programs?areaId=${encodeURIComponent(areaId)}&date=${encodeURIComponent(date)}`,
    ),
  search: (areaId: string, q: string) =>
    api.get<ProgramItem[]>(
      `/api/programs/search?areaId=${encodeURIComponent(areaId)}&q=${encodeURIComponent(q)}`,
    ),
  detail: (programId: number) =>
    api.get<ProgramDetailResponse>(`/api/programs/${programId}`),
};
