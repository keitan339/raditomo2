import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import { Navigate, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { GoogleLogin, GoogleOAuthProvider } from '@react-oauth/google';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';

export function LoginPage() {
  const isAuthed = useAuthStore((s) => !!s.accessToken);
  const { data: cfg, isLoading: cfgLoading, error: cfgError } = useQuery({
    queryKey: ['auth-config'],
    queryFn: () => authApi.config(),
    staleTime: Infinity,
    retry: false,
  });

  if (isAuthed) {
    return <Navigate to="/" replace />;
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent>
          <Stack spacing={3} alignItems="center" sx={{ py: 2 }}>
            <Typography variant="h4" sx={{ fontWeight: 700 }}>
              Raditomo
            </Typography>
            {cfgLoading && <CircularProgress size={24} />}
            {cfgError && (
              <Alert severity="error" sx={{ width: '100%' }}>
                認証構成の取得に失敗しました。バックエンドが起動しているか確認してください。
              </Alert>
            )}
            {cfg?.idp === 'google' && cfg.googleClientId && (
              <GoogleSignInArea clientId={cfg.googleClientId} />
            )}
            {cfg && cfg.idp !== 'google' && <RedirectLoginArea idp={cfg.idp} />}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

/**
 * 本番（Google OAuth）用: GIS（Google Identity Services）の公式ボタンを描画。
 * ID Token 取得 → /api/auth/google/id-token に POST。
 */
function GoogleSignInArea({ clientId }: { clientId: string }) {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [error, setError] = useState<string | null>(null);

  return (
    <>
      <Typography variant="body2" color="text.secondary">
        Google アカウントでログイン
      </Typography>
      {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
      <GoogleOAuthProvider clientId={clientId}>
        <GoogleLogin
          theme="filled_blue"
          shape="rectangular"
          text="signin_with"
          useOneTap={false}
          onSuccess={async (cred) => {
            const idToken = cred.credential;
            if (!idToken) {
              setError('Google から ID Token を受け取れませんでした');
              return;
            }
            try {
              const result = await authApi.idTokenLogin(idToken);
              setAuth(result.accessToken, result.user);
              navigate('/', { replace: true });
            } catch (e) {
              setError(e instanceof Error ? e.message : 'ログイン処理に失敗しました');
            }
          }}
          onError={() => setError('Google ログインがキャンセルされました')}
        />
      </GoogleOAuthProvider>
    </>
  );
}

/**
 * ローカル開発（Keycloak）/ 動作確認用 IdP 用: 認可コードフロー。
 * 本番ではない旨を文言で示し、Google ブランドは使わない。
 */
function RedirectLoginArea({ idp }: { idp: string }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onClick = async () => {
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
    <>
      <Typography variant="body2" color="text.secondary">
        ローカル {idp === 'keycloak' ? 'Keycloak' : 'IdP'} で開発用ログイン
      </Typography>
      {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
      <Button
        variant="outlined"
        size="large"
        fullWidth
        onClick={onClick}
        disabled={loading}
        startIcon={loading ? <CircularProgress size={16} /> : undefined}
      >
        ログイン（{idp}）
      </Button>
    </>
  );
}
