import { Box, Stack, Tab, Tabs, Typography } from '@mui/material';
import { useState } from 'react';
import type { ProgramItem, StationGroup } from '../../types/api';
import { ProgramCard } from './ProgramCard';

interface Props {
  stations: StationGroup[];
  onSelect: (p: ProgramItem) => void;
}

/**
 * モバイル/タブレット向けリスト表示。横スクロールタブで放送局を切替し、
 * 選択中の局の番組を時刻順カードで縦並び表示する。
 */
export function ProgramList({ stations, onSelect }: Props) {
  const [active, setActive] = useState(0);
  if (stations.length === 0) {
    return (
      <Box sx={{ p: 4, textAlign: 'center', color: 'text.secondary' }}>
        放送局がありません
      </Box>
    );
  }
  const current = stations[Math.min(active, stations.length - 1)];

  return (
    <Stack spacing={2}>
      <Tabs
        value={Math.min(active, stations.length - 1)}
        onChange={(_, v) => setActive(v)}
        variant="scrollable"
        scrollButtons="auto"
      >
        {stations.map((st) => (
          <Tab key={st.stationId} label={st.name} />
        ))}
      </Tabs>
      {current.programs.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
          番組情報がありません
        </Typography>
      ) : (
        <Stack spacing={1}>
          {current.programs.map((p) => (
            <ProgramCard key={p.id} program={p} onClick={onSelect} />
          ))}
        </Stack>
      )}
    </Stack>
  );
}
