import { createTheme } from '@mui/material/styles';
import { jaJP } from '@mui/material/locale';
import { jaJP as datePickersJaJP } from '@mui/x-date-pickers/locales';

const fontStack = ['"Noto Sans JP"', '"Hiragino Sans"', '"Yu Gothic"', 'sans-serif'].join(',');

export const theme = createTheme(
  {
    palette: {
      mode: 'light',
      primary: { main: '#1976d2' },
      secondary: { main: '#ff5252' },
    },
    typography: {
      fontFamily: fontStack,
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true } },
    },
  },
  jaJP,
  datePickersJaJP,
);
