# 設計フェーズ ドキュメント一覧

## ドキュメント

| # | ファイル | 内容 |
|---|---------|------|
| 1 | [01-database-design.md](./01-database-design.md) | DB設計（ER図・テーブル定義・インデックス・クエリ例） |
| 2 | [02-api-design.md](./02-api-design.md) | REST API エンドポイント一覧・認証フロー |
| 3 | [03-radiko-integration.md](./03-radiko-integration.md) | ラジコAPI連携（auth、番組表、チャンク並列DL、HLS変換） |
| 4 | [04-frontend-design.md](./04-frontend-design.md) | 画面構成、技術スタック、画面別仕様 |
| 5 | [05-batch-file-notification.md](./05-batch-file-notification.md) | バッチ連携、ファイル保存、通知設計 |
| 6 | [06-docker-security.md](./06-docker-security.md) | Docker構成、Nginx設定、JWT・HLS配信の認証 |
| 7 | [07-test-strategy.md](./07-test-strategy.md) | テスト戦略（UT/IT/ST 3層、カバレッジ、CI） |

## 設計の主要な決定事項（要件定義から引き継ぎ・本設計で確定）

| 項目 | 決定 |
|------|------|
| DBマイグレーション | Flyway |
| CLIライブラリ | Picocli |
| F4→F2連携 | Spring ApplicationEvent + @TransactionalEventListener |
| バッチ排他制御 | batch_executions テーブル + SELECT FOR UPDATE |
| Web経由バッチ実行 | @Async + ポーリング状態確認 |
| HTTPクライアント（ラジコ） | Java 21 標準 HttpClient + Virtual Threads |
| ID3タグ | mp3agic |
| Gmail認証 | アプリパスワード方式 |
| バッジ表示 | 30秒間隔ポーリング |
| フロントUI | React 18 + TypeScript + Vite + MUI v5 |
| サーバー状態管理 | TanStack Query |
| HLSプレイヤー | hls.js + Safari ネイティブ自動切替 |
| JWT署名 | HS256、アクセストークン24h、リフレッシュトークンなし |
| HLS配信認証 | 署名付きパス + Nginx auth_request |
| トークン保存場所 | SessionStorage（ブラウザセッション中のみ・毎回認証） |
| PostgreSQL | 16 (alpine) |
| ログ | Logback、ファイルローテート（日次・100MB・30日保持）。`app.log` と `batch.log` を分離 |
| バッチ自動実行 | 毎朝5:30 JST に F4→F2（cron `0 30 5 * * *` Asia/Tokyo） |
| F2 処理単位 | 1番組ずつ直列。チャンクのみ並列（並列度8） |
| タイムフリー期限判定 | Java 側で「放送日（5:00区切り）+ 8日 5:00:00 JST」を厳密判定 |
| エリア制限 | フリー会員のため、サーバー設置場所のラジコ `auth2` が返す area_id のみ聴取可能 |
| テスト戦略 | UT（Mockito / Vitest+RTL+MSW）/ IT（Testcontainers + GreenMail + mock-oauth2-server + WireMock + Playwright）/ ST（半自動スクリプト） |
| カバレッジ目標 | UT 80%（JaCoCo / Vitest coverage）。CI で未達なら失敗 |
| CI | GitHub Actions（UT/IT 自動、ST は workflow_dispatch） |
