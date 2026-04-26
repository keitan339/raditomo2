import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, Typography } from '@mui/material';
import { Navigate } from 'react-router-dom';
import { useState } from 'react';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';

export function LoginPage() {
  const isAuthed = useAuthStore((s) => !!s.accessToken);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (isAuthed) {
    return <Navigate to="/" replace />;
  }

  const handleLogin = async () => {
    setLoading(true);
    setError(null);
    try {
      const { authUrl } = await authApi.loginUrl();
      window.location.href = authUrl;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'ログインURLの取得に失敗しました');
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent>
          <Stack spacing={3} alignItems="center" sx={{ py: 2 }}>
            <Typography variant="h4" sx={{ fontWeight: 700 }}>
              Raditomo
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Google アカウントでログイン
            </Typography>
            {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={handleLogin}
              disabled={loading}
              startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
            >
              Google でログイン
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
