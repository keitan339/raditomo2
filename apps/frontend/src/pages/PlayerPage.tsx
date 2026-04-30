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
import Hls from 'hls.js';
import { useEffect, useRef } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { recordingsApi } from '../api/recordings';
import { getAccessToken } from '../store/authStore';
import { formatRange } from '../lib/time';

const SAVE_INTERVAL_MS = 5000;

export function PlayerPage() {
  const { historyId } = useParams();
  const id = historyId ? Number(historyId) : null;
  const audioRef = useRef<HTMLAudioElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const lastSavedRef = useRef<number>(0);
  const initialPositionAppliedRef = useRef(false);

  const { data: recording, isLoading: loadingRec, error } = useQuery({
    queryKey: ['recording', id],
    queryFn: () => recordingsApi.detail(id!),
    enabled: id != null,
  });

  const { data: position } = useQuery({
    queryKey: ['playback-position', id],
    queryFn: () => recordingsApi.getPosition(id!),
    enabled: id != null,
  });

  // メディアソース接続（HLS or ネイティブ）
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !recording?.hlsUrl) return;
    if (Hls.isSupported()) {
      const hls = new Hls({
        // /hls/* は Nginx の auth_request で JWT 検証されるため、
        // playlist と segment いずれの XHR にも Authorization を付ける。
        xhrSetup: (xhr) => {
          const token = getAccessToken();
          if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
        },
      });
      hls.loadSource(recording.hlsUrl);
      hls.attachMedia(audio);
      hlsRef.current = hls;
      return () => {
        hls.destroy();
        hlsRef.current = null;
      };
    }
    // Safari ネイティブ HLS。Authorization ヘッダを乗せられないため、
    // 同一オリジン Cookie 認証が必要な環境では別途対応が必要。
    audio.src = recording.hlsUrl;
  }, [recording?.hlsUrl]);

  // 初回再生位置の復元
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !position) return;
    if (initialPositionAppliedRef.current) return;
    const apply = () => {
      if (!initialPositionAppliedRef.current && audio.duration > 0) {
        audio.currentTime = Math.min(position.positionSeconds, audio.duration - 1);
        initialPositionAppliedRef.current = true;
      }
    };
    audio.addEventListener('loadedmetadata', apply, { once: true });
    if (audio.readyState >= 1) apply();
    return () => audio.removeEventListener('loadedmetadata', apply);
  }, [position]);

  // 5秒間隔で再生位置を保存（再生中のみ）
  useEffect(() => {
    if (id == null) return;
    const audio = audioRef.current;
    if (!audio) return;

    const save = (force = false) => {
      const sec = Math.floor(audio.currentTime);
      if (!force && Math.abs(sec - lastSavedRef.current) < 1) return;
      lastSavedRef.current = sec;
      recordingsApi.putPosition(id, sec).catch(() => undefined);
    };

    const onTimeUpdate = () => {
      // 5秒間隔。1秒以下の差は無視。
      const sec = Math.floor(audio.currentTime);
      if (sec - lastSavedRef.current >= 5 || sec < lastSavedRef.current) {
        save();
      }
    };
    const onPause = () => save(true);
    const onEnded = () => save(true);

    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('pause', onPause);
    audio.addEventListener('ended', onEnded);
    const onUnload = () => save(true);
    window.addEventListener('beforeunload', onUnload);

    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('pause', onPause);
      audio.removeEventListener('ended', onEnded);
      window.removeEventListener('beforeunload', onUnload);
      // 画面離脱時にも保存
      if (audio.currentTime > 0) save(true);
    };
  }, [id]);

  // タイマーで定期保存（timeupdate が来ない backstop）
  useEffect(() => {
    if (id == null) return;
    const t = setInterval(() => {
      const audio = audioRef.current;
      if (!audio || audio.paused) return;
      const sec = Math.floor(audio.currentTime);
      if (sec === lastSavedRef.current) return;
      lastSavedRef.current = sec;
      recordingsApi.putPosition(id, sec).catch(() => undefined);
    }, SAVE_INTERVAL_MS);
    return () => clearInterval(t);
  }, [id]);

  return (
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Button component={RouterLink} to="/library" size="small">
          ← ライブラリへ戻る
        </Button>
      </Stack>

      {loadingRec && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && <Alert severity="error">録音情報の取得に失敗しました</Alert>}

      {recording && (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6">{recording.programTitle}</Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {formatRange(recording.broadcastStartAt, recording.broadcastEndAt)} / {recording.stationId}
            </Typography>
            {recording.performers && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                出演: {recording.performers}
              </Typography>
            )}
            {!recording.hlsUrl ? (
              <Alert severity="warning" sx={{ mt: 2 }}>
                再生URLがありません
              </Alert>
            ) : (
              <Box sx={{ mt: 2 }}>
                <audio ref={audioRef} controls preload="metadata" style={{ width: '100%' }} />
                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                  5秒ごとに再生位置を保存します。次回開いた時は同じ位置から再開します。
                </Typography>
              </Box>
            )}
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}
