import { Box, Card, CardActionArea, Chip, Stack, Typography } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import ScheduleIcon from '@mui/icons-material/Schedule';
import HistoryIcon from '@mui/icons-material/History';
import type { ProgramItem } from '../../types/api';
import { formatRange } from '../../lib/time';

interface Props {
  program: ProgramItem;
  onClick?: (p: ProgramItem) => void;
  dense?: boolean;
}

export function ProgramCard({ program, onClick, dense }: Props) {
  const isRegistered = !!program.registration;
  const palette = pickPalette(program);

  return (
    <Card
      variant="outlined"
      sx={{
        // 登録済みだけ青枠（primary.main の太線 + 薄い青背景）。
        // 過去/未来は背景色（grey.100 / background.paper）とチップで区別する。
        borderColor: isRegistered ? 'primary.main' : palette.borderColor,
        borderWidth: isRegistered ? 2 : 1,
        bgcolor: isRegistered
          ? (theme) => `${theme.palette.primary.main}14` // 約 8% アルファ
          : palette.bg,
      }}
    >
      <CardActionArea onClick={() => onClick?.(program)} sx={{ p: dense ? 1 : 1.5 }}>
        <Stack spacing={0.5}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="caption" color="text.secondary">
              {formatRange(program.broadcastStartAt, program.broadcastEndAt)}
            </Typography>
            {program.isPast ? (
              <Chip
                size="small"
                color="default"
                icon={<HistoryIcon />}
                label="放送済"
                variant="outlined"
              />
            ) : (
              <Chip
                size="small"
                color="info"
                icon={<ScheduleIcon />}
                label="予定"
                variant="outlined"
              />
            )}
            {isRegistered && (
              <Chip
                size="small"
                color="primary"
                icon={<StarIcon />}
                label={program.registration?.type === 'WEEKLY' ? '毎週' : '一回'}
              />
            )}
          </Stack>
          <Typography variant={dense ? 'body2' : 'subtitle2'} sx={{ fontWeight: 600 }} noWrap>
            {program.title}
          </Typography>
          {program.performers && (
            <Typography variant="caption" color="text.secondary" noWrap>
              {program.performers}
            </Typography>
          )}
          {!program.isWithinTimefreeWindow && program.isPast && (
            <Box>
              <Chip size="small" color="warning" label="期限切れ" variant="outlined" />
            </Box>
          )}
        </Stack>
      </CardActionArea>
    </Card>
  );
}

function pickPalette(p: ProgramItem) {
  if (p.isPast) {
    // 放送済み: 落ち着いたグレー背景 + 中立色の枠
    return { bg: 'grey.100', borderColor: 'divider' as const };
  }
  // 未来: 白背景 + 中立色の枠（青系は登録済みの目印として温存）
  return { bg: 'background.paper', borderColor: 'divider' as const };
}
