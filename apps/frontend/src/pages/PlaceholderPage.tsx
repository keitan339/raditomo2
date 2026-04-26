import { Box, Typography } from '@mui/material';

interface Props {
  title: string;
}

export function PlaceholderPage({ title }: Props) {
  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 2 }}>
        {title}
      </Typography>
      <Typography color="text.secondary">この画面は次のサブフェーズで実装予定です。</Typography>
    </Box>
  );
}
