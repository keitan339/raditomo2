import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Pagination,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { DownloadStatus, HistoryItem } from '../types/api';
import { historiesApi } from '../api/histories';
import { formatBroadcastDateRange } from '../lib/time';

const STATUS_OPTIONS: { value: 'ALL' | DownloadStatus; label: string }[] = [
  { value: 'ALL', label: '全て' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失敗' },
  { value: 'EXPIRED', label: '期限切れ' },
];

export function HistoryPage() {
  const [status, setStatus] = useState<'ALL' | DownloadStatus>('ALL');
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<HistoryItem | null>(null);
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['histories', status, page],
    queryFn: () => historiesApi.list(status === 'ALL' ? null : status, page, 50),
    placeholderData: (prev) => prev,
  });

  // 履歴ページを開いたら未読バッジを既読化
  useEffect(() => {
    historiesApi
      .markSeen()
      .then(() => qc.invalidateQueries({ queryKey: ['badge'] }))
      .catch(() => undefined);
  }, [qc]);

  const remove = useMutation({
    mutationFn: (id: number) => historiesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['histories'] });
      qc.invalidateQueries({ queryKey: ['badge'] });
      setTarget(null);
    },
  });

  return (
    <Stack spacing={2}>
      <Typography variant="h5">ダウンロード履歴</Typography>
      <TextField
        select
        size="small"
        label="ステータス"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as 'ALL' | DownloadStatus);
          setPage(0);
        }}
        sx={{ width: 200 }}
      >
        {STATUS_OPTIONS.map((o) => (
          <MenuItem key={o.value} value={o.value}>
            {o.label}
          </MenuItem>
        ))}
      </TextField>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && <Alert severity="error">履歴の取得に失敗しました</Alert>}
      {!isLoading && (data?.content ?? []).length === 0 && (
        <Alert severity="info">該当する履歴はありません</Alert>
      )}
      {(data?.content ?? []).length > 0 && (
        <Stack spacing={1}>
          {(data?.content ?? []).map((h) => (
            <HistoryCard key={h.id} item={h} onDelete={() => setTarget(h)} />
          ))}
        </Stack>
      )}
      {(data?.totalPages ?? 0) > 1 && (
        <Pagination
          count={data!.totalPages}
          page={page + 1}
          onChange={(_, p) => setPage(p - 1)}
          sx={{ display: 'flex', justifyContent: 'center' }}
        />
      )}

      <Dialog open={target != null} onClose={() => setTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>履歴を削除しますか？</DialogTitle>
        <DialogContent>
          <DialogContentText>{deletePromptFor(target)}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTarget(null)}>キャンセル</Button>
          <Button
            color="error"
            variant="contained"
            disabled={remove.isPending}
            onClick={() => target && remove.mutate(target.id)}
          >
            削除
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function HistoryCard({ item, onDelete }: { item: HistoryItem; onDelete: () => void }) {
  const expired = isExpired(item);
  return (
    <Card variant="outlined">
      <CardContent
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          '&:last-child': { pb: 2 },
        }}
      >
        <Box>
          <StatusIcon status={item.status} expired={expired} />
        </Box>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
            <Chip size="small" variant="outlined" label={item.stationName} />
            <Typography variant="caption" color="text.secondary">
              {formatBroadcastDateRange(item.broadcastStartAt, item.broadcastEndAt)}
            </Typography>
            <Chip
              size="small"
              color={statusColor(item.status, expired)}
              label={statusLabel(item.status, expired)}
            />
          </Stack>
          <Typography variant="subtitle2" noWrap>
            {item.programTitle}
          </Typography>
          {item.errorMessage && (
            <Typography variant="caption" color="error" sx={{ display: 'block' }}>
              {item.errorMessage}
            </Typography>
          )}
        </Box>
        <Button color="error" size="small" onClick={onDelete}>
          削除
        </Button>
      </CardContent>
    </Card>
  );
}

function StatusIcon({ status, expired }: { status: DownloadStatus; expired: boolean }) {
  if (status === 'FAILED') return <ErrorOutlineIcon color="error" />;
  if (expired || status === 'EXPIRED') return <WarningAmberIcon color="warning" />;
  return <CheckCircleOutlineIcon color="success" />;
}

function statusLabel(status: DownloadStatus, expired: boolean): string {
  if (status === 'FAILED') return '失敗';
  if (status === 'EXPIRED') return '期限切れ';
  return expired ? '期限切れ済み' : '成功';
}

function statusColor(
  status: DownloadStatus,
  expired: boolean,
): 'error' | 'warning' | 'success' | 'default' {
  if (status === 'FAILED') return 'error';
  if (status === 'EXPIRED' || expired) return 'warning';
  return 'success';
}

function isExpired(item: HistoryItem | null): boolean {
  if (!item) return false;
  // 放送日 + 8 日 5:00 JST
  const start = new Date(item.broadcastStartAt);
  const expires = new Date(start.getTime());
  expires.setUTCDate(expires.getUTCDate() + 8);
  // start を JST 換算した「放送日」を取って、その +8 日 5:00 JST にする。
  // ただしフロントは「概算」で十分（厳密判定はバックエンドで EXPIRED に遷移させる）。
  return Date.now() > expires.getTime();
}

function deletePromptFor(item: HistoryItem | null): string {
  if (!item) return '';
  const expired = item.status === 'EXPIRED' || (item.status === 'SUCCESS' && isExpired(item));
  if (expired) {
    return 'この番組は再ダウンロードできません。履歴を削除しますか？';
  }
  return '履歴を削除すると次回バッチで再ダウンロード対象になります。削除しますか？';
}
