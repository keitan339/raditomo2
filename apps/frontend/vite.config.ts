import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    // DevContainer / Docker など inotify watcher 上限の低い環境向けにポーリングへ切替
    watch: {
      usePolling: true,
      interval: 500,
      ignored: ['**/node_modules/**', '**/dist/**', '**/coverage/**', '**/playwright-report/**', '**/e2e/**'],
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/test/**',
        'src/main.tsx',
        'src/vite-env.d.ts',
      ],
      // TODO(coverage): テスト追加でカバレッジ 80% 到達後 NO_COVERAGE_CHECK を外す
      thresholds: process.env.NO_COVERAGE_CHECK
        ? undefined
        : {
            lines: 80,
            functions: 80,
            branches: 80,
            statements: 80,
          },
    },
  },
});
