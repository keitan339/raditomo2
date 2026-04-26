import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';

export function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [error, setError] = useState<string | null>(null);
  // React 18 StrictMode 下で useEffect が dev 中 2 回走り、認可コードが二重消費されると
  // 2 回目で IdP から "Code not valid" が返って失敗する。1 回目だけ処理するためのガード。
  const handledRef = useRef(false);

  useEffect(() => {
    if (handledRef.current) return;
    handledRef.current = true;

    const code = searchParams.get('code');
    const state = searchParams.get('state');
    if (!code || !state) {
      setError('認可コードまたは state が指定されていません');
      return;
    }
    (async () => {
      try {
        const result = await authApi.callback(code, state);
        setAuth(result.accessToken, result.user);
        navigate('/', { replace: true });
      } catch (e) {
        setError(e instanceof Error ? e.message : 'ログイン処理に失敗しました');
      }
    })();
  }, [searchParams, setAuth, navigate]);

  if (error) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}>
        <Stack spacing={2} sx={{ maxWidth: 420, width: '100%' }}>
          <Alert severity="error">{error}</Alert>
          <Button variant="contained" onClick={() => navigate('/login', { replace: true })}>
            ログイン画面へ戻る
          </Button>
        </Stack>
      </Box>
    );
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Stack spacing={2} alignItems="center">
        <CircularProgress />
        <Typography>ログイン処理中…</Typography>
      </Stack>
    </Box>
  );
}
