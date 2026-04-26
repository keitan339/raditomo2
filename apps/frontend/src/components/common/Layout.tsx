import { Box, Container } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';

export function Layout() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppHeader />
      <Container maxWidth="xl" sx={{ flexGrow: 1, py: 3 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
