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
  Stack,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { RecordingResponse } from '../types/api';
import { recordingsApi } from '../api/recordings';
import { formatRange } from '../lib/time';

export function LibraryDetailPage() {
  const { title: rawTitle } = useParams();
  const title = rawTitle ? decodeURIComponent(rawTitle) : '';
  const qc = useQueryClient();
  const [target, setTarget] = useState<RecordingResponse | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['recordings-by-title', title],
    queryFn: () => recordingsApi.byTitle(title),
    enabled: !!title,
  });

  const remove = useMutation({
    mutationFn: (id: number) => recordingsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['recordings-by-title', title] });
      qc.invalidateQueries({ queryKey: ['recordings-groups'] });
      setTarget(null);
    },
  });

  return (
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" spacing={2}>
        <Button component={RouterLink} to="/library" size="small">
          ← ライブラリへ戻る
        </Button>
      </Stack>
      <Typography variant="h5">{title}</Typography>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && <Alert severity="error">録音一覧の取得に失敗しました</Alert>}
      {!isLoading && (data ?? []).length === 0 && (
        <Alert severity="info">この番組の録音はまだありません</Alert>
      )}
      <Stack spacing={1}>
        {(data ?? []).map((r) => (
          <Card key={r.historyId} variant="outlined">
            <CardContent
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                '&:last-child': { pb: 2 },
              }}
            >
              <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                  <Chip size="small" variant="outlined" label={r.stationName} />
                  <Typography variant="caption" color="text.secondary">
                    {formatRange(r.broadcastStartAt, r.broadcastEndAt)}
                  </Typography>
                  {!r.reDownloadable && (
                    <Chip size="small" color="warning" variant="outlined" label="期限切れ" />
                  )}
                </Stack>
                {r.performers && (
                  <Typography variant="caption" color="text.secondary">
                    出演: {r.performers}
                  </Typography>
                )}
              </Box>
              <Button
                variant="contained"
                size="small"
                component={RouterLink}
                to={`/player/${r.historyId}`}
                startIcon={<PlayArrowIcon />}
                disabled={!r.hlsUrl && !r.mp3Path}
              >
                再生
              </Button>
              <Button color="error" size="small" onClick={() => setTarget(r)}>
                削除
              </Button>
            </CardContent>
          </Card>
        ))}
      </Stack>

      <Dialog open={target != null} onClose={() => setTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>録音ファイルを削除しますか？</DialogTitle>
        <DialogContent>
          <DialogContentText>
            録音ファイル（MP3 / HLS）を削除します。履歴は残ります。
            {target && !target.reDownloadable && (
              <>
                <br />
                <strong>この番組はタイムフリー期限が切れているため、削除すると再ダウンロードできません。</strong>
              </>
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTarget(null)}>キャンセル</Button>
          <Button
            color="error"
            variant="contained"
            disabled={remove.isPending}
            onClick={() => target && remove.mutate(target.historyId)}
          >
            削除
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
