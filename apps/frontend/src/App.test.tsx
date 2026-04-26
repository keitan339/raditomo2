import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { theme } from './theme/theme';

function renderWith(initialPath = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialPath]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('redirects unauthenticated users to login', () => {
    renderWith('/');
    // ログイン画面のヘッダー文言で判定（実際のボタンは /api/auth/config のレスポンス
    // により Google / Keycloak / custom で切り替わるため）
    expect(screen.getByText('Raditomo')).toBeInTheDocument();
  });

  it('shows app name on login page', () => {
    renderWith('/login');
    expect(screen.getByText('Raditomo')).toBeInTheDocument();
  });
});
