import { api, ApiError } from './client';
import type {
  CreateRegistrationRequest,
  RegistrationResponse,
} from '../types/api';

export const registrationsApi = {
  list: () => api.get<RegistrationResponse[]>('/api/registrations'),
  create: (req: CreateRegistrationRequest) =>
    api.post<RegistrationResponse>('/api/registrations', req),
  delete: (id: number) => api.del<void>(`/api/registrations/${id}`),
};

export function isConflictError(e: unknown): boolean {
  return e instanceof ApiError && e.status === 409;
}
