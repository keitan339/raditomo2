import { api } from './client';
import type {
  BatchExecutionResponse,
  RunBatchRequest,
  RunBatchResponse,
} from '../types/api';

export const batchApi = {
  run: (req: RunBatchRequest) =>
    api.post<RunBatchResponse>('/api/batch/run', req),
  execution: (id: number) =>
    api.get<BatchExecutionResponse>(`/api/batch/executions/${id}`),
  listRunning: () =>
    api.get<BatchExecutionResponse[]>('/api/batch/executions?status=RUNNING'),
};
