import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '../components/common/Layout';
import { AuthGuard } from '../components/common/AuthGuard';
import { LoginPage } from '../pages/LoginPage';
import { OAuthCallbackPage } from '../pages/OAuthCallbackPage';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { ProgramTablePage } from '../pages/ProgramTablePage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<OAuthCallbackPage />} />
      <Route
        path="/"
        element={
          <AuthGuard>
            <Layout />
          </AuthGuard>
        }
      >
        <Route index element={<ProgramTablePage />} />
        <Route path="registrations" element={<PlaceholderPage title="登録一覧" />} />
        <Route path="library" element={<PlaceholderPage title="ライブラリ" />} />
        <Route path="library/:title" element={<PlaceholderPage title="ライブラリ - 番組" />} />
        <Route path="player/:historyId" element={<PlaceholderPage title="プレイヤー" />} />
        <Route path="history" element={<PlaceholderPage title="履歴" />} />
        <Route path="settings" element={<PlaceholderPage title="設定" />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
