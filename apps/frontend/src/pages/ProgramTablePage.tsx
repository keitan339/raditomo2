import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import FilterListIcon from '@mui/icons-material/FilterList';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addDays, broadcastDate, compactYmd, formatJpDate } from '../lib/time';
import { programsApi } from '../api/programs';
import { areasApi } from '../api/areas';
import { userSettingsApi } from '../api/userSettings';
import type { ProgramItem, StationGroup } from '../types/api';
import { ProgramGrid } from '../components/program/ProgramGrid';
import { ProgramList } from '../components/program/ProgramList';
import { ProgramCard } from '../components/program/ProgramCard';
import { ProgramDetailDialog } from '../components/program/ProgramDetailDialog';

const DATE_OPTION_BACK_DAYS = 7;
const DATE_OPTION_FORWARD_DAYS = 7;

export function ProgramTablePage() {
  const theme = useTheme();
  const isWide = useMediaQuery(theme.breakpoints.up('lg'));

  const today = broadcastDate();
  const [date, setDate] = useState<string>(today);
  const [areaIdOverride, setAreaIdOverride] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [selected, setSelected] = useState<{ id: number; fallback: ProgramItem | null } | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);

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

  const visibility = useQuery({
    queryKey: ['station-visibility', areaId],
    queryFn: () => userSettingsApi.visibility(areaId!),
    enabled: areaId != null,
    staleTime: 5 * 60_000,
  });

  const searchResults = useQuery({
    queryKey: ['programs-search', areaId, searchTerm],
    queryFn: () => programsApi.search(areaId!, searchTerm),
    enabled: areaId != null && searchTerm.trim().length > 0,
  });

  const visibleSet = useMemo(() => {
    // 未設定（visibility 配列に無い）局はデフォルト表示。
    const map = new Map<string, boolean>();
    (visibility.data ?? []).forEach((v) => map.set(v.stationId, v.visible));
    return map;
  }, [visibility.data]);

  const isStationVisible = (stationId: string) => visibleSet.get(stationId) ?? true;

  const stations = useMemo<StationGroup[]>(
    () => (programs.data?.stations ?? []).filter((s) => isStationVisible(s.stationId)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [programs.data, visibleSet],
  );

  const showSearch = searchTerm.trim().length > 0;
  const loading = settings.isLoading || areas.isLoading || (areaId != null && programs.isLoading);

  const dateOptions = useMemo(() => {
    const opts: string[] = [];
    for (let i = -DATE_OPTION_BACK_DAYS; i <= DATE_OPTION_FORWARD_DAYS; i++) {
      opts.push(addDays(today, i));
    }
    return opts;
    // today はマウント時の固定値。日が変わったら別マウントで再計算する。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Stack spacing={2}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        alignItems={{ xs: 'stretch', md: 'center' }}
        sx={{ px: 2 }}
      >
        <TextField
          select
          size="small"
          label="放送日"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          sx={{ minWidth: 160 }}
        >
          {dateOptions.map((d) => (
            <MenuItem key={d} value={d}>
              {formatJpDate(d)}
              {d === today ? '（今日）' : ''}
            </MenuItem>
          ))}
        </TextField>
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
        <Button
          variant="outlined"
          startIcon={<FilterListIcon />}
          onClick={() => setFilterOpen(true)}
          disabled={!programs.data}
        >
          局フィルタ
        </Button>
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
        <Alert severity="info" sx={{ mx: 2 }}>エリアを選択してください</Alert>
      )}
      {!loading && programs.error && (
        <Alert severity="error" sx={{ mx: 2 }}>番組表の取得に失敗しました</Alert>
      )}
      {!loading && showSearch && (
        <Box sx={{ px: 2 }}>
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
          <Box sx={{ px: 2 }}>
            <ProgramList
              stations={stations}
              onSelect={(p) => setSelected({ id: p.id, fallback: p })}
            />
          </Box>
        )
      )}
      {!loading && !showSearch && areaId != null && stations.length === 0 && !programs.error && (
        <Alert severity="info" sx={{ mx: 2 }}>
          表示できる放送局がありません。「局フィルタ」または「設定 → 放送局表示」で表示する局を選んでください。
        </Alert>
      )}
      <ProgramDetailDialog
        open={selected != null}
        programId={selected?.id ?? null}
        fallback={selected?.fallback ?? null}
        onClose={() => setSelected(null)}
      />
      <StationFilterDialog
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        areaId={areaId}
        stations={programs.data?.stations ?? []}
        isVisible={isStationVisible}
      />
    </Stack>
  );
}

function StationFilterDialog({
  open,
  onClose,
  areaId,
  stations,
  isVisible,
}: {
  open: boolean;
  onClose: () => void;
  areaId: string | null;
  stations: StationGroup[];
  isVisible: (stationId: string) => boolean;
}) {
  const qc = useQueryClient();
  const setVisibility = useMutation({
    mutationFn: ({ stationId, visible }: { stationId: string; visible: boolean }) =>
      userSettingsApi.updateVisibility(stationId, visible),
    onMutate: async ({ stationId, visible }) => {
      if (!areaId) return;
      await qc.cancelQueries({ queryKey: ['station-visibility', areaId] });
      qc.setQueryData<{ stationId: string; visible: boolean }[] | undefined>(
        ['station-visibility', areaId],
        (prev) => {
          const list = prev ?? [];
          const exists = list.some((v) => v.stationId === stationId);
          return exists
            ? list.map((v) => (v.stationId === stationId ? { ...v, visible } : v))
            : [...list, { stationId, visible }];
        },
      );
    },
    onSettled: () => {
      if (areaId) qc.invalidateQueries({ queryKey: ['station-visibility', areaId] });
    },
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>表示する放送局を選択</DialogTitle>
      <DialogContent dividers>
        {stations.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            放送局情報がありません
          </Typography>
        ) : (
          <Stack>
            {stations.map((s) => (
              <FormControlLabel
                key={s.stationId}
                control={
                  <Checkbox
                    checked={isVisible(s.stationId)}
                    onChange={(e) =>
                      setVisibility.mutate({
                        stationId: s.stationId,
                        visible: e.target.checked,
                      })
                    }
                  />
                }
                label={s.name}
              />
            ))}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>閉じる</Button>
      </DialogActions>
    </Dialog>
  );
}
