import {
  AppBar,
  Avatar,
  Badge,
  Box,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { authApi } from '../../api/auth';
import { useBadge } from '../../hooks/useBadge';

interface NavItem {
  label: string;
  to: string;
  showBadge?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: '番組表', to: '/' },
  { label: '登録一覧', to: '/registrations' },
  { label: 'ライブラリ', to: '/library' },
  { label: '履歴', to: '/history', showBadge: true },
  { label: '設定', to: '/settings' },
];

export function AppHeader() {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const { data: badge } = useBadge();
  const badgeCount = (badge?.failedCount ?? 0) + (badge?.expiredCount ?? 0);
  const [anchor, setAnchor] = useState<null | HTMLElement>(null);

  const onLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // SessionStorage クリアは最低限実施
    }
    clear();
    navigate('/login', { replace: true });
  };

  return (
    <AppBar position="sticky" color="default" elevation={1}>
      <Toolbar>
        <Typography
          variant="h6"
          component={RouterLink}
          to="/"
          sx={{ flexGrow: 0, mr: 3, color: 'inherit', textDecoration: 'none', fontWeight: 700 }}
        >
          Raditomo
        </Typography>
        {wide ? (
          <Box sx={{ flexGrow: 1, display: 'flex', gap: 1 }}>
            {NAV_ITEMS.map((item) => (
              <Button key={item.to} component={RouterLink} to={item.to} color="inherit">
                {item.showBadge ? (
                  <Badge color="error" badgeContent={badgeCount} max={99}>
                    {item.label}
                  </Badge>
                ) : (
                  item.label
                )}
              </Button>
            ))}
          </Box>
        ) : (
          <Box sx={{ flexGrow: 1 }}>
            <IconButton color="inherit" onClick={(e) => setAnchor(e.currentTarget)} aria-label="menu">
              <Badge color="error" badgeContent={badgeCount} max={99} invisible={badgeCount === 0}>
                <MenuIcon />
              </Badge>
            </IconButton>
            <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
              {NAV_ITEMS.map((item) => (
                <MenuItem
                  key={item.to}
                  component={RouterLink}
                  to={item.to}
                  onClick={() => setAnchor(null)}
                >
                  {item.showBadge ? (
                    <Badge color="error" badgeContent={badgeCount} max={99} sx={{ pr: 2 }}>
                      {item.label}
                    </Badge>
                  ) : (
                    item.label
                  )}
                </MenuItem>
              ))}
            </Menu>
          </Box>
        )}
        {user && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Avatar
              src={user.pictureUrl ?? undefined}
              alt={user.name ?? user.email}
              sx={{ width: 32, height: 32 }}
            />
            <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'inline' } }}>
              {user.name ?? user.email}
            </Typography>
            <Button color="inherit" onClick={onLogout} size="small">
              ログアウト
            </Button>
          </Box>
        )}
      </Toolbar>
    </AppBar>
  );
}
