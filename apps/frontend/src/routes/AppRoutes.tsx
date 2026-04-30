import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '../components/common/Layout';
import { AuthGuard } from '../components/common/AuthGuard';
import { LoginPage } from '../pages/LoginPage';
import { OAuthCallbackPage } from '../pages/OAuthCallbackPage';
import { ProgramTablePage } from '../pages/ProgramTablePage';
import { RegistrationsPage } from '../pages/RegistrationsPage';
import { HistoryPage } from '../pages/HistoryPage';
import { SettingsPage } from '../pages/SettingsPage';
import { LibraryPage } from '../pages/LibraryPage';
import { LibraryDetailPage } from '../pages/LibraryDetailPage';
import { PlayerPage } from '../pages/PlayerPage';

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
        <Route path="registrations" element={<RegistrationsPage />} />
        <Route path="library" element={<LibraryPage />} />
        <Route path="library/:title" element={<LibraryDetailPage />} />
        <Route path="player/:historyId" element={<PlayerPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
