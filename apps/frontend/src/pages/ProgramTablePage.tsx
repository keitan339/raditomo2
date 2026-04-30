import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { addDays, broadcastDate, compactYmd, formatJpDate } from '../lib/time';
import { programsApi } from '../api/programs';
import { areasApi } from '../api/areas';
import { userSettingsApi } from '../api/userSettings';
import type { ProgramItem } from '../types/api';
import { ProgramGrid } from '../components/program/ProgramGrid';
import { ProgramList } from '../components/program/ProgramList';
import { ProgramCard } from '../components/program/ProgramCard';
import { ProgramDetailDialog } from '../components/program/ProgramDetailDialog';

export function ProgramTablePage() {
  const theme = useTheme();
  const isWide = useMediaQuery(theme.breakpoints.up('lg'));

  const [date, setDate] = useState<string>(broadcastDate());
  const [areaIdOverride, setAreaIdOverride] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [selected, setSelected] = useState<{ id: number; fallback: ProgramItem | null } | null>(null);

  const settings = useQuery({
    queryKey: ['user-settings'],
    queryFn: () => userSettingsApi.get(),
    staleTime: 5 * 60_000,
  });
  const areas = useQuery({
    queryKey: ['areas'],
    queryFn: () => areasApi.list(),
    staleTime: 60 * 60_000,
  });

  const areaId = areaIdOverride ?? settings.data?.currentAreaId ?? null;

  const programs = useQuery({
    queryKey: ['programs', areaId, date],
    queryFn: () => programsApi.list(areaId!, compactYmd(date)),
    enabled: areaId != null,
  });

  const searchResults = useQuery({
    queryKey: ['programs-search', areaId, searchTerm],
    queryFn: () => programsApi.search(areaId!, searchTerm),
    enabled: areaId != null && searchTerm.trim().length > 0,
  });

  const stations = useMemo(() => programs.data?.stations ?? [], [programs.data]);

  const showSearch = searchTerm.trim().length > 0;
  const loading = settings.isLoading || areas.isLoading || (areaId != null && programs.isLoading);

  return (
    <Stack spacing={2}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        alignItems={{ xs: 'stretch', md: 'center' }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <IconButton onClick={() => setDate((d) => addDays(d, -1))} aria-label="前日">
            <ChevronLeftIcon />
          </IconButton>
          <Typography variant="h6" sx={{ minWidth: 120, textAlign: 'center' }}>
            {formatJpDate(date)}
          </Typography>
          <IconButton onClick={() => setDate((d) => addDays(d, 1))} aria-label="翌日">
            <ChevronRightIcon />
          </IconButton>
          <Button size="small" onClick={() => setDate(broadcastDate())}>
            今日
          </Button>
        </Stack>
        <TextField
          select
          size="small"
          label="エリア"
          value={areaId ?? ''}
          onChange={(e) => setAreaIdOverride(e.target.value)}
          sx={{ minWidth: 160 }}
          disabled={!areas.data}
        >
          {(areas.data ?? []).map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </TextField>
        <Stack direction="row" spacing={1} sx={{ flexGrow: 1 }}>
          <TextField
            size="small"
            label="検索"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') setSearchTerm(search);
            }}
            sx={{ flexGrow: 1 }}
          />
          <Button variant="outlined" onClick={() => setSearchTerm(search)}>
            検索
          </Button>
          {showSearch && (
            <Button
              onClick={() => {
                setSearch('');
                setSearchTerm('');
              }}
            >
              クリア
            </Button>
          )}
        </Stack>
      </Stack>
      <Divider />
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {!loading && areaId == null && (
        <Alert severity="info">エリアを選択してください</Alert>
      )}
      {!loading && programs.error && (
        <Alert severity="error">番組表の取得に失敗しました</Alert>
      )}
      {!loading && showSearch && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            検索結果（{searchResults.data?.length ?? 0} 件）
          </Typography>
          {searchResults.isLoading ? (
            <CircularProgress size={20} />
          ) : (searchResults.data ?? []).length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              該当する番組はありません
            </Typography>
          ) : (
            <Stack spacing={1}>
              {(searchResults.data ?? []).map((p) => (
                <ProgramCard
                  key={p.id}
                  program={p}
                  onClick={(prog) => setSelected({ id: prog.id, fallback: prog })}
                />
              ))}
            </Stack>
          )}
        </Box>
      )}
      {!loading && !showSearch && areaId != null && stations.length > 0 && (
        isWide ? (
          <ProgramGrid
            stations={stations}
            onSelect={(p) => setSelected({ id: p.id, fallback: p })}
          />
        ) : (
          <ProgramList
            stations={stations}
            onSelect={(p) => setSelected({ id: p.id, fallback: p })}
          />
        )
      )}
      {!loading && !showSearch && areaId != null && stations.length === 0 && !programs.error && (
        <Alert severity="info">
          番組情報がまだありません。設定 → バッチ手動実行 で取得してください。
        </Alert>
      )}
      <ProgramDetailDialog
        open={selected != null}
        programId={selected?.id ?? null}
        fallback={selected?.fallback ?? null}
        onClose={() => setSelected(null)}
      />
    </Stack>
  );
}
