import { Box, Stack, Typography } from '@mui/material';
import type { ProgramItem, StationGroup } from '../../types/api';
import { ProgramCard } from './ProgramCard';

interface Props {
  stations: StationGroup[];
  onSelect: (p: ProgramItem) => void;
}

const COLUMN_WIDTH = 220;
const HOUR_HEIGHT = 96; // px / 1時間（30分番組で 48px → タイトル 1 行が収まる）
const HOURS = Array.from({ length: 25 }, (_, i) => 5 + i); // 5:00〜29:00

/**
 * PC グリッド表示（縦=時刻、横=放送局）。
 * 各番組は放送時間に比例した高さで絶対配置する。
 */
export function ProgramGrid({ stations, onSelect }: Props) {
  return (
    <Box sx={{ overflow: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Box sx={{ display: 'flex', minWidth: 'fit-content' }}>
        <Box sx={{ width: 64, flexShrink: 0, position: 'sticky', left: 0, zIndex: 2, bgcolor: 'background.paper' }}>
          <Box sx={{ height: 48, borderBottom: 1, borderColor: 'divider' }} />
          {/* 番組カラム側の hour line と完全に揃えるため、軸ラベルも絶対配置で同じ y にする。 */}
          <Box sx={{ position: 'relative', height: HOURS.length * HOUR_HEIGHT }}>
            {HOURS.map((h) => (
              <Typography
                key={h}
                variant="caption"
                color="text.secondary"
                sx={{
                  position: 'absolute',
                  // ラベル top を行ライン（番組カード top と同じ y）に置く
                  top: (h - 5) * HOUR_HEIGHT,
                  left: 8,
                  lineHeight: 1,
                }}
              >
                {String(h).padStart(2, '0')}:00
              </Typography>
            ))}
          </Box>
        </Box>
        {stations.map((st) => (
          <Box
            key={st.stationId}
            sx={{
              width: COLUMN_WIDTH,
              flexShrink: 0,
              borderLeft: 1,
              borderColor: 'divider',
              position: 'relative',
            }}
          >
            <Box
              sx={{
                height: 48,
                borderBottom: 1,
                borderColor: 'divider',
                px: 1,
                display: 'flex',
                alignItems: 'center',
                position: 'sticky',
                top: 0,
                bgcolor: 'background.paper',
                zIndex: 1,
              }}
            >
              <Typography variant="subtitle2" noWrap>
                {st.name}
              </Typography>
            </Box>
            <Box sx={{ position: 'relative', height: HOURS.length * HOUR_HEIGHT }}>
              {HOURS.map((h) => (
                <Box
                  key={h}
                  sx={{
                    position: 'absolute',
                    top: (h - 5) * HOUR_HEIGHT,
                    left: 0,
                    right: 0,
                    borderTop: 1,
                    borderColor: 'divider',
                    height: HOUR_HEIGHT,
                  }}
                />
              ))}
              {st.programs.map((p) => {
                const top = minutesFromBroadcastStart(p.broadcastStartAt);
                const dur = durationMinutes(p.broadcastStartAt, p.broadcastEndAt);
                return (
                  <Box
                    key={p.id}
                    sx={{
                      position: 'absolute',
                      top: (top * HOUR_HEIGHT) / 60,
                      // 番組時間に厳密に比例。短い番組ははみ出ない範囲で切り詰める。
                      height: (dur * HOUR_HEIGHT) / 60,
                      left: 4,
                      right: 4,
                      overflow: 'hidden',
                    }}
                  >
                    <Stack sx={{ height: '100%' }}>
                      <ProgramCard program={p} onClick={onSelect} dense />
                    </Stack>
                  </Box>
                );
              })}
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

/** 放送開始時刻が「5:00 起点」で何分後か。深夜帯（hh<5）は +24 換算。 */
function minutesFromBroadcastStart(iso: string): number {
  const m = iso.match(/T(\d{2}):(\d{2})/);
  if (!m) return 0;
  const hour = Number(m[1]);
  const minute = Number(m[2]);
  const adjusted = hour < 5 ? hour + 24 : hour;
  return (adjusted - 5) * 60 + minute;
}

function durationMinutes(startIso: string, endIso: string): number {
  return Math.max(15, Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 60_000));
}
