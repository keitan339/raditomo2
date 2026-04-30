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
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { registrationsApi } from '../api/registrations';
import type { RegistrationResponse } from '../types/api';
import { formatRange } from '../lib/time';

const DOW_LABELS = ['日', '月', '火', '水', '木', '金', '土'];

export function RegistrationsPage() {
  const [tab, setTab] = useState<'dow' | 'type'>('dow');
  const [target, setTarget] = useState<RegistrationResponse | null>(null);
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['registrations'],
    queryFn: () => registrationsApi.list(),
  });

  const remove = useMutation({
    mutationFn: (id: number) => registrationsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['registrations'] });
      qc.invalidateQueries({ queryKey: ['programs'] });
      setTarget(null);
    },
  });

  const groups = useMemo(() => groupRegistrations(data ?? [], tab), [data, tab]);

  return (
    <Stack spacing={2}>
      <Typography variant="h5">登録一覧</Typography>
      <Tabs value={tab} onChange={(_, v) => setTab(v)}>
        <Tab value="dow" label="曜日別" />
        <Tab value="type" label="種類別" />
      </Tabs>
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && <Alert severity="error">登録一覧の取得に失敗しました</Alert>}
      {!isLoading && !error && (data ?? []).length === 0 && (
        <Alert severity="info">登録された番組はまだありません</Alert>
      )}
      {!isLoading && !error && groups.length > 0 && (
        <Stack spacing={3}>
          {groups.map((g) => (
            <Box key={g.label}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
                {g.label}
              </Typography>
              <Stack spacing={1}>
                {g.items.map((r) => (
                  <Card key={r.id} variant="outlined">
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
                          <Chip
                            size="small"
                            color={r.registrationType === 'WEEKLY' ? 'primary' : 'default'}
                            label={r.registrationType === 'WEEKLY' ? '毎週' : '一回'}
                          />
                          <Chip size="small" variant="outlined" label={r.stationId} />
                          <Typography variant="caption" color="text.secondary">
                            {formatRange(r.broadcastStartAt, r.broadcastEndAt)}
                          </Typography>
                        </Stack>
                        <Typography variant="subtitle2" noWrap>
                          {r.title}
                        </Typography>
                      </Box>
                      <Button
                        color="error"
                        size="small"
                        onClick={() => setTarget(r)}
                      >
                        削除
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </Box>
          ))}
        </Stack>
      )}
      <Dialog open={target != null} onClose={() => setTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>登録を削除しますか？</DialogTitle>
        <DialogContent>
          <DialogContentText>
            「{target?.title}」の登録を削除します。録音済みファイル・履歴は残ります。
          </DialogContentText>
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

interface Group {
  label: string;
  items: RegistrationResponse[];
}

function groupRegistrations(
  list: RegistrationResponse[],
  mode: 'dow' | 'type',
): Group[] {
  if (mode === 'type') {
    const weekly = list.filter((r) => r.registrationType === 'WEEKLY');
    const once = list.filter((r) => r.registrationType === 'ONCE');
    const groups: Group[] = [];
    if (weekly.length) groups.push({ label: '毎週', items: weekly });
    if (once.length) groups.push({ label: '一回限り', items: once });
    return groups;
  }
  // 曜日別: 0=日 .. 6=土。day_of_week が null の ONCE は broadcastStartAt から算出。
  const buckets: RegistrationResponse[][] = Array.from({ length: 7 }, () => []);
  for (const r of list) {
    const dow = r.dayOfWeek ?? dowOfIso(r.broadcastStartAt);
    buckets[dow].push(r);
  }
  return buckets
    .map((items, i) => ({ label: `${DOW_LABELS[i]}曜日`, items }))
    .filter((g) => g.items.length > 0)
    .map((g) => ({
      ...g,
      items: g.items
        .slice()
        .sort((a, b) => a.broadcastStartAt.localeCompare(b.broadcastStartAt)),
    }));
}

function dowOfIso(iso: string): number {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!m) return 0;
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
  return d.getUTCDay();
}
