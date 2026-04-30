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
  /** 実行中の音声 DL バッチ (F2 / F4_F2) を取得。バナー表示用。空配列なら未実行。 */
  runningDownload: () =>
    api.get<BatchExecutionResponse[]>('/api/batch/executions/running-download'),
};
