import { Alert, LinearProgress, Stack, Typography } from '@mui/material';
import DownloadingIcon from '@mui/icons-material/Downloading';
import { useQuery } from '@tanstack/react-query';
import { batchApi } from '../../api/batch';
import type { BatchExecutionResponse } from '../../types/api';

/**
 * 音声 DL バッチ (F2 / F4_F2) が実行中ならバナー表示。
 * 未実行ならコンポーネント自体がレンダリングされない。
 *
 * 5 秒ごとにポーリングし、開始からの経過時間を表示する。
 * SettingsPage / HistoryPage の先頭に置いて、別経路（スケジューラ・CLI・他タブ）で
 * 走った F2 もユーザーに見えるようにする目的。
 */
export function DownloadStatusBanner() {
  const { data } = useQuery({
    queryKey: ['running-download'],
    queryFn: () => batchApi.runningDownload(),
    refetchInterval: 5000,
  });
  const exec = (data ?? [])[0];
  if (!exec) return null;
  return <Banner exec={exec} />;
}

function Banner({ exec }: { exec: BatchExecutionResponse }) {
  const startedAt = new Date(exec.startedAt);
  const valid = !Number.isNaN(startedAt.getTime());
  const elapsedMin = valid
    ? Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 60_000))
    : null;
  const hh = valid ? String(startedAt.getHours()).padStart(2, '0') : '';
  const mm = valid ? String(startedAt.getMinutes()).padStart(2, '0') : '';
  const typeLabel = exec.type === 'F4_F2' ? '番組表取得＋音声DL' : '音声DL';
  const triggeredLabel =
    exec.triggeredBy === 'SCHEDULER'
      ? 'スケジューラ起動'
      : exec.triggeredBy === 'CLI'
        ? 'CLI 起動'
        : 'Web 起動';

  return (
    <Alert
      severity="info"
      icon={<DownloadingIcon fontSize="inherit" />}
      sx={{ '& .MuiAlert-message': { width: '100%' } }}
    >
      <Stack spacing={0.5}>
        <Typography variant="body2">
          {typeLabel}を実行中
          {elapsedMin != null && (
            <>（{elapsedMin}分前 {hh}:{mm} に {triggeredLabel}）</>
          )}
        </Typography>
        <LinearProgress />
        {exec.summary && (
          <Typography variant="caption" color="text.secondary">
            {exec.summary}
          </Typography>
        )}
      </Stack>
    </Alert>
  );
}
