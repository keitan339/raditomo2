# 実装フェーズへの引き継ぎドキュメント

## このドキュメントの位置付け

設計フェーズ → 実装フェーズの橋渡し文書。実装着手時に読むべき情報を集約する。
- 要件定義 → 設計フェーズの引き継ぎは `docs/archive/handover.md` を参照
- 設計の最新状態は `docs/design/` 配下のドキュメントが正

## プロジェクト概要

ラジコ（radiko）のタイムフリー番組をダウンロードし、HLSストリーミングで再生するWebアプリケーション。個人利用、自宅サーバー稼働、Docker Compose 3コンテナ構成。

- 公開ドメイン: `raditomo.hidenv.com`
- ウォーターフォール開発: 要件定義完了 → 設計完了 → **次は実装**

---

## 成果物一覧

### 要件定義（凍結）
| ファイル | 内容 |
|---------|------|
| `docs/requirements/01-functional-requirements.md` | 機能要件（F1〜F5 + 将来対応） |
| `docs/requirements/02-technical-requirements.md` | 技術要件（システム構成・非機能・技術スタック） |

### 設計（凍結。仕様変更が必要なら本ドキュメントを更新）
| ファイル | 内容 |
|---------|------|
| `docs/design/00-design-overview.md` | 設計全体目次・主要決定事項一覧 |
| `docs/design/01-database-design.md` | ER図・テーブル定義・インデックス・動的照合クエリ例 |
| `docs/design/02-api-design.md` | REST APIエンドポイント・認証フロー |
| `docs/design/03-radiko-integration.md` | ラジコAPI連携（auth1/auth2、番組表、チャンク並列DL） |
| `docs/design/04-frontend-design.md` | 画面構成・MUI・レスポンシブ・HLSプレイヤー |
| `docs/design/05-batch-file-notification.md` | スケジューラ（5:30）・F4→F2連鎖・ファイル保存・Gmail通知 |
| `docs/design/06-docker-security.md` | Docker構成・Nginx・JWT・HLS配信認証 |
| `docs/design/07-test-strategy.md` | UT/IT/ST 3層、ツール、カバレッジ80%、CI |

### 運用ドキュメント
| ファイル | 内容 |
|---------|------|
| `deployment/docs/01-initial-setup.md` | 初回デプロイ手順（前提・セットアップ・チェックリスト） |
| `deployment/docs/02-update.md` | 2回目以降の更新フロー・日常運用・証明書更新・トラブルシュート |

### 過去フェーズ
| ファイル | 内容 |
|---------|------|
| `docs/archive/handover.md` | 要件→設計フェーズの引き継ぎ |

---

## 主要技術選定（再掲）

| カテゴリ | 選定 |
|---------|------|
| Backend | Java 21 / Spring Boot / Maven |
| DB | PostgreSQL 16 / Flyway |
| Frontend | React 18 + TypeScript + Vite + **MUI v5** + TanStack Query + Zustand |
| HLS再生 | hls.js + Safari ネイティブ |
| Container | Docker Compose（Nginx / App / DB） |
| Auth | Google OAuth 2.0 + JWT (HS256, 24h, リフレッシュトークンなし, SessionStorage) |
| 通知 | Gmail SMTP（アプリパスワード方式） |
| バッチ | Spring Scheduler（cron `0 30 5 * * *` Asia/Tokyo） |
| CLI | Picocli（ホストラッパー `./raditomo`） |
| ID3 | ffmpeg `-metadata`（ID3v2.3）。Duration は MP3 フレームヘッダで再現。mp3agic は依存に残し将来必要時のみ使う |
| HTTP（ラジコ） | Java 21 標準 HttpClient + Virtual Threads |
| MP3 | 128 kbps CBR |
| HLS変換 | ffmpeg |
| ログ | Logback（`app.log` / `batch.log` 分離、100MB/30日） |
| メール | `[Raditomo]` プレフィックス |
| HLS配信 | Nginx auth_request + 署名付きパス |
| テスト | JUnit5+Mockito+AssertJ / Vitest+RTL+MSW / Testcontainers / GreenMail / mock-oauth2-server / WireMock / Playwright |
| CI | GitHub Actions |
| カバレッジ | JaCoCo / Vitest coverage、行 80% 以上で CI 失敗 |

---

## 推奨実装順序

依存関係を踏まえ、フェーズ単位で段階的に進める。

### フェーズ1: プロジェクトスケルトン
1. Maven プロジェクト初期化（`pom.xml`、依存関係）
2. Vite + React + TypeScript プロジェクト初期化（`package.json`）
3. Docker Compose 雛形（`deployment/docker-compose.yml`、`deployment/.env.example`）
4. CI 雛形（`.github/workflows/ci.yml`）
5. Flyway 初期マイグレーション（`V1__initial_schema.sql`、`V2__seed_master_data.sql`）

### フェーズ2: 基盤レイヤー
6. JPA Entity / Repository（全テーブル）
7. Google OAuth + JWT 認証（`/api/auth/*`）
8. mock-oauth2-server を IT で使えるようにする
9. ユーザー許可リスト CLI（`./raditomo users add/remove/list`）
10. SecurityConfig（認証必須エンドポイント設定）

### フェーズ3: ラジコ連携
11. RadikoAuthService（auth1/auth2）
12. RadikoProgramFetcher（番組表XML取得・パース）
13. WireMock を使ったラジコ IT セットアップ
14. RadikoTimefreeDownloader（チャンク並列DL）
15. Mp3Encoder（ffmpeg 連結 → MP3）+ ID3タグ付与
16. HlsConverter（MP3 → HLS）

### フェーズ4: バッチ
17. F4 番組表取得バッチ + Spring Scheduler（5:30）
18. F2 タイムフリーDLバッチ + 動的照合ロジック
19. F4 → F2 連鎖（ApplicationEvent）
20. 排他制御（`batch_executions` + SELECT FOR UPDATE）
21. CLI サブコマンド（`download-programs` / `download-audio` / `download`）
22. Web 経由バッチ起動API（`/api/batch/run`、@Async）

### フェーズ5: 通知
23. Gmail SMTP 送信（spring-boot-starter-mail）
24. メールテンプレート（失敗・期限切れ）
25. 失敗・期限切れバッジ API（`/api/histories/badge`）
26. GreenMail を使ったメール IT

### フェーズ6: フロントエンド
27. MUI テーマ設定（日本語フォント Noto Sans JP）
28. 認証まわり（Login画面、認証ガード、SessionStorage）
29. 番組表画面（PC グリッド / スマホ リスト切替、登録モーダル）
30. 登録一覧画面・履歴画面・設定画面
31. ライブラリ画面・再生画面（hls.js）
32. レジューム再生（5秒間隔保存）
33. バッジ表示（30秒ポーリング）
34. バッチ手動実行UI

### フェーズ7: 統合・最終化
35. Nginx 設定（HTTPS、HLS配信、auth_request）
36. HLS 署名付きパスの実装
37. Playwright Browser IT（主要シナリオ）
38. ST スクリプト（`scripts/st/*`）
39. Let's Encrypt 証明書取得（HTTP-01）
40. 本番デプロイ手順書

各フェーズで「UT を書く → 実装 → IT を書く → 通る」を繰り返す。

---

## 実装フェーズで決めるべきこと

設計では確定していない、実装着手時に決める項目。

### 優先度: 高
- [ ] **パッケージ構造**: `com.raditomo.<module>` の具体的なモジュール割り（auth, radiko, batch, program, registration, history, recording, common など）
- [ ] **ラジコ共通鍵の取得方針**: rec_radiko_ts と同じ鍵をハードコードする方法。鍵が無効化された場合のフォールバック
- [ ] **チャンク並列度の実測**: 8並列で 403/429 が出ないか確認、必要なら調整
- [ ] **ffmpeg コマンドの最終形**: AAC連結→MP3、HLS変換のオプション（ノイズ・音質を実音源で確認）
- [ ] **HLS 署名付きパスの具体実装**: `/hls/<token>/<userId>/<title>/<date>/playlist.m3u8` の token フォーマットとplaylist内のセグメントURL書き換え方式

### 優先度: 中
- [ ] **CSRF トークン実装の必要性**: SessionStorage + Bearer 方式のため不要だが、念のため確認
- [ ] **DBコネクションプール設定**（HikariCP デフォルト値で良いか）
- [ ] **Spring `@Async` のスレッドプール設定**（`batchTaskExecutor` のサイズ）
- [ ] **エラー画面・グローバルエラーハンドラ**（Frontend）
- [ ] **i18n の必要性**: 日本語固定で十分か（要件は日本語のみ）→ おそらく不要
- [ ] **ログイン保持の挙動微調整**: タブを開いて初回アクセス時の Google 自動再認証フローのUX

### 優先度: 低
- [ ] **アプリのアイコン・ロゴ**
- [ ] **メールテンプレートの装飾**（HTML メール化するかプレーンテキストか）
- [ ] **メトリクス収集**（Spring Boot Actuator の有効化範囲）
- [ ] **healthcheck エンドポイント**（`/api/health`）

---

## 設計時に意識的に決めた判断（背景）

実装時に再検討する場合は背景を踏まえること。

| 判断 | 決定 | 背景 |
|------|------|------|
| F2 並列処理の単位 | **番組単位は直列**、チャンクのみ並列 | レート制限・エラー処理のシンプルさ |
| 毎週マッチング手順4 | 番組名不一致でも必ずDL | 番組終了・差替え検知のため |
| 認証方式 | アクセストークンのみ（リフレッシュなし） | ブラウザセッション中のみログイン保持。毎回認証 |
| トークン保存場所 | SessionStorage | XSS耐性、タブ単位のセッション分離 |
| Gmail SMTP | アプリパスワード方式 | 個人利用、OAuth2 設定の煩雑さ回避 |
| バッジ更新 | 30秒ポーリング | 個人利用・更新頻度低、WebSocket は過剰 |
| 番組表UI | レスポンシブ自動切替（PC=グリッド / スマホ=リスト） | PC/スマホ両対応 |
| F4 取得方式 | エリア単位API（全局1リクエスト） | リクエスト数削減 |
| F4→F2 連鎖 | ApplicationEvent + AFTER_COMMIT | F4 部分失敗時もF2が動く |
| バッチ排他制御 | `batch_executions` + SELECT FOR UPDATE | DBで一元管理、in-memory ロックは避ける |
| ファイル削除と履歴 | 履歴は残す（`file_deleted_at` セット） | 削除済みを再DL対象にしない |
| カバレッジ目標 | UT のみ 80% | IT/ST はシナリオで担保 |
| ブラウザE2E | IT 層に配置 | 独自サービス（mock-oauth等）で完結させるため |
| ST | 半自動スクリプト主体 | リリース前スモーク用途 |

---

## 開発環境の現状

DevContainer（`.devcontainer/`）にセットアップ済み:
- Node.js 22 / Java 21 / Maven 3.9 / ffmpeg / Docker / Playwright
- タイムゾーン: JST (Asia/Tokyo)

実装フェーズで追加が必要なもの:
- IT 用 `docker-compose.test.yml`（mock-oauth2-server など）
- 開発用 `.env`（リポジトリ除外、`.env.example` をコミット）
- 本番用 SSL 証明書取得手順（Let's Encrypt + HTTP-01、Value Domain でDNS設定）

---

## 実装着手前のチェックリスト

- [ ] Google Cloud Console で OAuth 2.0 クライアント作成（リダイレクトURI: `https://raditomo.hidenv.com/auth/callback` ← SPA のルートに渡す。フロントが code/state を受けて `/api/auth/google/callback` に中継する）
- [ ] Gmail アプリパスワード発行（送信元アカウント）
- [ ] Value Domain で `raditomo.hidenv.com` の A レコードを自宅サーバーグローバルIPへ
- [ ] 自宅ルーターで 80/443 ポート転送
- [ ] GitHub リポジトリ作成（プライベート想定）
- [ ] GitHub Secrets に `.env` 相当の値を登録（CI 用）
