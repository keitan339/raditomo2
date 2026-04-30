import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';

export function Layout() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppHeader />
      <Box component="main" sx={{ flexGrow: 1, py: 3 }}>
        <Outlet />
      </Box>
    </Box>
  );
}
