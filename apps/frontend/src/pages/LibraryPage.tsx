import {
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { recordingsApi } from '../api/recordings';

export function LibraryPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['recordings-groups'],
    queryFn: () => recordingsApi.groups(),
  });

  return (
    <Stack spacing={2}>
      <Typography variant="h5">ライブラリ</Typography>
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && <Alert severity="error">ライブラリの取得に失敗しました</Alert>}
      {!isLoading && (data ?? []).length === 0 && (
        <Alert severity="info">録音された番組はまだありません</Alert>
      )}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            sm: '1fr 1fr',
            md: '1fr 1fr 1fr',
          },
          gap: 2,
        }}
      >
        {(data ?? []).map((g) => (
          <Card key={g.title} variant="outlined">
            <CardActionArea
              component={RouterLink}
              to={`/library/${encodeURIComponent(g.title)}`}
            >
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap>
                  {g.title}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {g.count} 回 / 最新: {formatLatest(g.latestBroadcastAt)}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Box>
    </Stack>
  );
}

function formatLatest(iso: string): string {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!m) return '';
  return `${m[1]}/${Number(m[2])}/${Number(m[3])}`;
}
