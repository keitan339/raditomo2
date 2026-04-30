import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { areasApi } from '../api/areas';
import { userSettingsApi } from '../api/userSettings';
import { batchApi } from '../api/batch';
import { BatchExecutionPanel } from '../components/batch/BatchExecutionPanel';
import { DownloadStatusBanner } from '../components/batch/DownloadStatusBanner';

export function SettingsPage() {
  const qc = useQueryClient();
  const [pendingArea, setPendingArea] = useState<string | null>(null);
  const [tracking, setTracking] = useState<number | null>(null);

  const settings = useQuery({
    queryKey: ['user-settings'],
    queryFn: () => userSettingsApi.get(),
  });
  const areas = useQuery({
    queryKey: ['areas'],
    queryFn: () => areasApi.list(),
    staleTime: 60 * 60_000,
  });
  const visibility = useQuery({
    queryKey: ['station-visibility', settings.data?.currentAreaId],
    queryFn: () => userSettingsApi.visibility(settings.data!.currentAreaId),
    enabled: settings.data?.currentAreaId != null,
  });
  const stations = useQuery({
    queryKey: ['stations', settings.data?.currentAreaId],
    queryFn: () => areasApi.stations(settings.data!.currentAreaId),
    enabled: settings.data?.currentAreaId != null,
  });

  const updateArea = useMutation({
    mutationFn: (areaId: string) => userSettingsApi.update(areaId),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['user-settings'] });
      qc.invalidateQueries({ queryKey: ['programs'] });
      qc.invalidateQueries({ queryKey: ['stations'] });
      qc.invalidateQueries({ queryKey: ['station-visibility'] });
      setTracking(res.areaChangeFetchStatus.batchExecutionId);
      setPendingArea(null);
    },
  });

  const setVisibility = useMutation({
    mutationFn: ({ stationId, visible }: { stationId: string; visible: boolean }) =>
      userSettingsApi.updateVisibility(stationId, visible),
    onMutate: async ({ stationId, visible }) => {
      await qc.cancelQueries({ queryKey: ['station-visibility'] });
      qc.setQueryData<{ stationId: string; visible: boolean }[] | undefined>(
        ['station-visibility', settings.data?.currentAreaId],
        (prev) => {
          if (!prev) return prev;
          const found = prev.some((v) => v.stationId === stationId);
          return found
            ? prev.map((v) => (v.stationId === stationId ? { ...v, visible } : v))
            : [...prev, { stationId, visible }];
        },
      );
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['station-visibility'] });
      qc.invalidateQueries({ queryKey: ['programs'] });
    },
  });

  return (
    <Stack spacing={3}>
      <Typography variant="h5">設定</Typography>

      <DownloadStatusBanner />

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            エリア
          </Typography>
          {settings.isLoading || areas.isLoading ? (
            <CircularProgress size={24} />
          ) : (
            <TextField
              select
              size="small"
              label="エリア"
              value={settings.data?.currentAreaId ?? ''}
              onChange={(e) => setPendingArea(e.target.value)}
              sx={{ width: 240 }}
            >
              {(areas.data ?? []).map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {tracking != null && <AreaChangeProgress executionId={tracking} onDone={() => setTracking(null)} />}
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            放送局表示
          </Typography>
          {stations.isLoading || visibility.isLoading ? (
            <CircularProgress size={24} />
          ) : (stations.data ?? []).length === 0 ? (
            <Alert severity="info">放送局がまだありません。番組表バッチを実行してください。</Alert>
          ) : (
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1 }}>
              {(stations.data ?? []).map((st) => {
                const v = (visibility.data ?? []).find((vv) => vv.stationId === st.id);
                const checked = v ? v.visible : true; // デフォ表示
                return (
                  <FormControlLabel
                    key={st.id}
                    control={
                      <Checkbox
                        checked={checked}
                        onChange={(e) =>
                          setVisibility.mutate({ stationId: st.id, visible: e.target.checked })
                        }
                      />
                    }
                    label={st.name}
                  />
                );
              })}
            </Box>
          )}
        </CardContent>
      </Card>

      <BatchExecutionPanel />

      <ConfirmAreaDialog
        open={pendingArea != null}
        areaName={(areas.data ?? []).find((a) => a.id === pendingArea)?.name ?? pendingArea ?? ''}
        onCancel={() => setPendingArea(null)}
        onConfirm={() => pendingArea && updateArea.mutate(pendingArea)}
        loading={updateArea.isPending}
      />
    </Stack>
  );
}

function ConfirmAreaDialog({
  open,
  areaName,
  onCancel,
  onConfirm,
  loading,
}: {
  open: boolean;
  areaName: string;
  onCancel: () => void;
  onConfirm: () => void;
  loading: boolean;
}) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>エリアを変更しますか？</DialogTitle>
      <DialogContent>
        <DialogContentText>
          エリアを「{areaName}」に変更します。番組表を再取得しますがしばらく時間がかかります。
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>キャンセル</Button>
        <Button variant="contained" disabled={loading} onClick={onConfirm}>
          変更する
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function AreaChangeProgress({
  executionId,
  onDone,
}: {
  executionId: number;
  onDone: () => void;
}) {
  const { data } = useQuery({
    queryKey: ['batch-execution', executionId],
    queryFn: () => batchApi.execution(executionId),
    refetchInterval: (q) => {
      const status = (q.state.data as { status?: string } | undefined)?.status;
      return status === 'RUNNING' ? 2000 : false;
    },
  });
  useEffect(() => {
    if (data && data.status !== 'RUNNING') {
      const t = setTimeout(onDone, 1500);
      return () => clearTimeout(t);
    }
  }, [data, onDone]);
  if (!data) return null;
  if (data.status === 'RUNNING') {
    return (
      <Box sx={{ mt: 2 }}>
        <Typography variant="caption">番組表を取得中…</Typography>
        <LinearProgress />
      </Box>
    );
  }
  if (data.status === 'FAILED' || data.status === 'PARTIAL_FAILURE') {
    return (
      <Alert severity="warning" sx={{ mt: 2 }}>
        番組表の取得が完了しませんでした。{data.summary ?? ''}
      </Alert>
    );
  }
  return (
    <Alert severity="success" sx={{ mt: 2 }}>
      番組表の取得が完了しました
    </Alert>
  );
}
