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
        borderColor: isRegistered ? 'primary.main' : palette.borderColor,
        borderWidth: isRegistered ? 2 : 1,
        bgcolor: palette.bg,
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
    return { bg: 'grey.50', borderColor: 'divider' as const };
  }
  return { bg: 'background.paper', borderColor: 'info.light' as const };
}
