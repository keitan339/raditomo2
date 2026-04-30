import { Box, Card, CardActionArea, Chip, Stack, Typography } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import HistoryIcon from '@mui/icons-material/History';
import type { ProgramItem } from '../../types/api';
import { formatJpDate, formatRange } from '../../lib/time';

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
        // 登録済みは青系の枠 + 薄い青背景（box-shadow で外側に追加し、レイアウトに影響しない）。
        // 過去/未来は背景色（grey.100 / background.paper）と「放送済」チップで区別する。
        borderColor: palette.borderColor,
        borderWidth: 1,
        // 番組表は升目表示なので角丸は不要
        borderRadius: 0,
        bgcolor: isRegistered
          ? (theme) => `${theme.palette.primary.main}14` // 約 8% アルファ
          : palette.bg,
        boxShadow: isRegistered
          ? (theme) => `inset 0 0 0 1px ${theme.palette.primary.main}`
          : 'none',
        // 親 Box の height（=放送時間に比例）にフィットさせる。
        height: dense ? '100%' : 'auto',
      }}
    >
      <CardActionArea
        onClick={() => onClick?.(program)}
        sx={{
          p: dense ? 0.75 : 1.5,
          height: '100%',
          // ButtonBase は inline-flex で中央寄せがデフォルト。dense モードでは
          // コンテンツを上揃えにして、放送開始時刻にタイトルが来るようにする。
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          justifyContent: 'flex-start',
        }}
      >
        <Stack spacing={dense ? 0.25 : 0.5}>
          {/* dense（グリッド）モードでは時刻/放送済チップは時間軸と背景色で代替するので省略 */}
          {!dense && (
            <Stack spacing={0.5}>
              <Typography variant="caption" color="text.secondary">
                {program.stationName} ・ {formatJpDate(program.broadcastDate)} ・{' '}
                {formatRange(program.broadcastStartAt, program.broadcastEndAt)}
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                {program.isPast && (
                  <Chip
                    size="small"
                    color="default"
                    icon={<HistoryIcon />}
                    label="放送済"
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
            </Stack>
          )}
          <Stack
            direction="row"
            spacing={0.5}
            alignItems="flex-start"
            sx={{ width: '100%', pr: dense && isRegistered ? 0.75 : 0 }}
          >
            <Typography
              variant={dense ? 'caption' : 'subtitle2'}
              sx={{
                fontWeight: 600,
                flexGrow: 1,
                minWidth: 0,
                // 折り返しを許可し、最大3行で省略する。
                display: '-webkit-box',
                WebkitLineClamp: 3,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                wordBreak: 'break-word',
                lineHeight: 1.3,
              }}
            >
              {program.title}
            </Typography>
            {dense && isRegistered && (
              <StarIcon fontSize="small" color="primary" sx={{ flexShrink: 0, mt: '1px' }} />
            )}
          </Stack>
          {!dense && program.performers && (
            <Typography variant="caption" color="text.secondary" noWrap>
              {program.performers}
            </Typography>
          )}
          {!dense && !program.isWithinTimefreeWindow && program.isPast && (
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
  // 未来: 白背景 + 中立色の枠
  return { bg: 'background.paper', borderColor: 'divider' as const };
}
