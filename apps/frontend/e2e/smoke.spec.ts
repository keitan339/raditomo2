import { test, expect } from '@playwright/test';

// 最小スモークテスト: SPA がビルド/配信され、未認証なら /login にリダイレクトし
// "Raditomo" ヘッダが表示されることだけ確認する。
// バックエンドが未起動でも動くよう、認証構成 API の結果には依存しない。
test('未認証アクセスはログイン画面にリダイレクトされる', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'Raditomo' })).toBeVisible();
});
