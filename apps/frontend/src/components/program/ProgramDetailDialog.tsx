import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Radio,
  RadioGroup,
  Stack,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ProgramItem, RegistrationType } from '../../types/api';
import { programsApi } from '../../api/programs';
import { isConflictError, registrationsApi } from '../../api/registrations';
import { formatRange } from '../../lib/time';

interface Props {
  open: boolean;
  programId: number | null;
  fallback?: ProgramItem | null;
  onClose: () => void;
}

const DOW_LABELS = ['日', '月', '火', '水', '木', '金', '土'];

export function ProgramDetailDialog({ open, programId, fallback, onClose }: Props) {
  const qc = useQueryClient();
  const [type, setType] = useState<RegistrationType>('WEEKLY');
  const [error, setError] = useState<string | null>(null);

  const { data: detail, isLoading } = useQuery({
    queryKey: ['program', programId],
    queryFn: () => programsApi.detail(programId!),
    enabled: open && programId != null,
  });

  // 表示用は detail があれば優先、なければ fallback。
  const display = detail ?? fallback ?? null;
  const registration = display?.registration ?? null;

  useEffect(() => {
    if (open) {
      setType(registration?.type ?? 'WEEKLY');
      setError(null);
    }
  }, [open, registration?.type]);

  const dowLabel = useMemo(() => {
    if (!display) return '';
    const m = display.broadcastStartAt.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (!m) return '';
    const date = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
    return DOW_LABELS[date.getUTCDay()];
  }, [display]);

  const create = useMutation({
    mutationFn: () =>
      registrationsApi.create({
        stationId: detail!.stationId,
        title: detail!.title,
        broadcastStartAt: detail!.broadcastStartAt,
        broadcastEndAt: detail!.broadcastEndAt,
        registrationType: type,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['programs'] });
      qc.invalidateQueries({ queryKey: ['registrations'] });
      qc.invalidateQueries({ queryKey: ['program', programId] });
      onClose();
    },
    onError: (e) => {
      setError(isConflictError(e) ? '既に登録されています' : (e instanceof Error ? e.message : '登録に失敗しました'));
    },
  });

  const remove = useMutation({
    mutationFn: () => registrationsApi.delete(registration!.registrationId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['programs'] });
      qc.invalidateQueries({ queryKey: ['registrations'] });
      qc.invalidateQueries({ queryKey: ['program', programId] });
      onClose();
    },
    onError: (e) => {
      setError(e instanceof Error ? e.message : '登録解除に失敗しました');
    },
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{display?.title ?? '読み込み中…'}</DialogTitle>
      <DialogContent dividers>
        {isLoading && !display ? (
          <Typography variant="body2">読み込み中…</Typography>
        ) : display ? (
          <Stack spacing={2}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {detail?.stationName ?? detail?.stationId ?? ''}
              </Typography>
              <Typography variant="body2">
                {formatRange(display.broadcastStartAt, display.broadcastEndAt)}
              </Typography>
              {display.performers && (
                <Typography variant="body2" color="text.secondary">
                  出演: {display.performers}
                </Typography>
              )}
            </Box>
            {detail?.description && (
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {detail.description}
              </Typography>
            )}
            {error && <Alert severity="error">{error}</Alert>}
            {!registration ? (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  登録方法
                </Typography>
                <RadioGroup value={type} onChange={(e) => setType(e.target.value as RegistrationType)}>
                  <FormControlLabel
                    value="WEEKLY"
                    control={<Radio />}
                    label={`毎週（${dowLabel}曜 ${formatRange(display.broadcastStartAt, display.broadcastEndAt)}）`}
                  />
                  <FormControlLabel value="ONCE" control={<Radio />} label="一回限り" />
                </RadioGroup>
              </Box>
            ) : (
              <Alert severity="info">
                登録済み（{registration.type === 'WEEKLY' ? '毎週' : '一回限り'}）です。解除するとダウンロード対象から外れます。
              </Alert>
            )}
          </Stack>
        ) : (
          <Typography variant="body2">番組情報を取得できません</Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>閉じる</Button>
        {display && !registration && (
          <Button
            variant="contained"
            onClick={() => create.mutate()}
            disabled={!detail || create.isPending}
          >
            登録する
          </Button>
        )}
        {display && registration && (
          <Button
            variant="outlined"
            color="error"
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
          >
            登録解除
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
