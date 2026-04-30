import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  LinearProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { BatchType } from '../../types/api';
import { batchApi } from '../../api/batch';
import { ApiError } from '../../api/client';

type BatchKey = BatchType;

const BATCHES: { key: BatchKey; label: string; description: string }[] = [
  { key: 'F4', label: 'F4: 番組表取得', description: '放送局・番組情報を取得します' },
  { key: 'F2', label: 'F2: ダウンロード', description: 'タイムフリー音声をダウンロードします' },
  { key: 'F4_F2', label: 'F4 → F2 一括', description: '番組表取得＋ダウンロードを連続実行します' },
];

export function BatchExecutionPanel() {
  const [date, setDate] = useState('');
  const [force, setForce] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tracking, setTracking] = useState<number | null>(null);
  const qc = useQueryClient();

  const run = useMutation({
    mutationFn: (type: BatchKey) =>
      batchApi.run({
        type,
        options:
          type === 'F4'
            ? undefined
            : { ...(date ? { date: date.replaceAll('-', '') } : {}), force },
      }),
    onSuccess: (res) => {
      setError(null);
      setTracking(res.batchExecutionId);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409) {
        setError('既に同じバッチが実行中です');
      } else {
        setError(e instanceof Error ? e.message : 'バッチ起動に失敗しました');
      }
    },
  });

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" gutterBottom>
          バッチ手動実行
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2, flexWrap: 'wrap' }}>
          <TextField
            type="date"
            size="small"
            label="日付指定（F2/F4_F2）"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
          />
          <FormControlLabel
            control={<Checkbox checked={force} onChange={(e) => setForce(e.target.checked)} />}
            label="強制実行（成功済みも再DL）"
          />
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          {BATCHES.map((b) => (
            <Box key={b.key} sx={{ flex: 1 }}>
              <Button
                variant="outlined"
                fullWidth
                disabled={run.isPending}
                onClick={() => run.mutate(b.key)}
              >
                {b.label}
              </Button>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                {b.description}
              </Typography>
            </Box>
          ))}
        </Stack>
        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
        {tracking != null && (
          <ExecutionTracker
            executionId={tracking}
            onCleared={() => setTracking(null)}
            onSettled={() => {
              qc.invalidateQueries({ queryKey: ['programs'] });
              qc.invalidateQueries({ queryKey: ['stations'] });
              qc.invalidateQueries({ queryKey: ['histories'] });
              qc.invalidateQueries({ queryKey: ['badge'] });
              qc.invalidateQueries({ queryKey: ['recordings-groups'] });
            }}
          />
        )}
      </CardContent>
    </Card>
  );
}

function ExecutionTracker({
  executionId,
  onCleared,
  onSettled,
}: {
  executionId: number;
  onCleared: () => void;
  onSettled: () => void;
}) {
  const { data } = useQuery({
    queryKey: ['batch-execution', executionId],
    queryFn: () => batchApi.execution(executionId),
    refetchInterval: (q) => {
      const status = (q.state.data as { status?: string } | undefined)?.status;
      return status === 'RUNNING' ? 2000 : false;
    },
  });

  const finished = !!data && data.status !== 'RUNNING';
  useEffect(() => {
    if (finished) onSettled();
  }, [finished, onSettled]);
  if (!data) {
    return (
      <Box sx={{ mt: 2 }}>
        <CircularProgress size={20} />
      </Box>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="caption">
        {data.type} #{data.id} - {data.status}
      </Typography>
      {data.status === 'RUNNING' && <LinearProgress />}
      {data.summary && (
        <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
          {data.summary}
        </Typography>
      )}
      {finished && (
        <Button size="small" onClick={onCleared} sx={{ mt: 1 }}>
          閉じる
        </Button>
      )}
    </Box>
  );
}
