# デプロイ手順

自宅サーバーへの初回デプロイから日次運用までの手順書。`raditomo.hidenv.com` で公開する想定。

## 前提条件

| 項目 | 値・確認方法 |
|---|---|
| OS | Linux（Ubuntu 22.04 LTS で動作確認） |
| Docker | Docker Engine 24+ / `docker compose` v2 |
| ディスク | 録音用に 50GB 以上推奨（番組数 × 平均 100MB） |
| メモリ | 2GB 以上（Spring + Postgres + Nginx + ffmpeg 並列処理） |
| ネットワーク | グローバル IPv4 / 80・443 がインターネットから届くこと |
| ドメイン | `raditomo.hidenv.com` の A レコードがサーバー外部 IP を指すこと（Value Domain で管理） |
| ルーター | 外部 80/443 → サーバーへポート転送 |
| Google OAuth | OAuth クライアント発行済み（Authorized redirect URI に `https://raditomo.hidenv.com/auth/callback` を登録） |
| Gmail SMTP | アプリパスワードを発行済み |

DNS とルーターの設定は **Let's Encrypt の HTTP-01 チャレンジで証明書取得する前**に必須。先に整えておく。

---

## 初回セットアップ

### 1. リポジトリ取得

```bash
git clone <repo-url> raditomo
cd raditomo
```

### 2. 環境変数ファイル作成

```bash
cp .env.example .env
$EDITOR .env
```

設定する項目:

| 変数 | 説明 |
|---|---|
| `DB_PASSWORD` | Postgres パスワード（任意の強い文字列） |
| `JWT_SECRET` | HS256 シークレット。`openssl rand -hex 32` などで生成 |
| `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` | Google Cloud Console で発行した値 |
| `GOOGLE_OAUTH_REDIRECT_URI` | `https://raditomo.hidenv.com/auth/callback` |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Gmail アプリパスワード |
| `APP_BASE_URL` | `https://raditomo.hidenv.com` |
| `RADIKO_DOWNLOAD_CONCURRENCY` | 並列度。デフォルト 8 |

`.env` は **絶対にリポジトリにコミットしない**（`.gitignore` で除外済み）。

### 3. フロントエンドビルド

`docker-compose.yml` は `./apps/frontend/dist` を Nginx にマウントするため、**ホスト側で先にビルドしておく**必要がある。

```bash
cd apps/frontend
npm ci
npm run build
cd ../..
```

`apps/frontend/dist/index.html` が生成されていること。

### 4. Let's Encrypt 証明書を取得

DNS とルーター設定が済んでいることを確認してから:

```bash
./scripts/init-letsencrypt.sh raditomo.hidenv.com you@example.com
```

スクリプトは以下を自動で行う:

1. 仮の自己署名証明書を生成（Nginx を起動するため）
2. Nginx だけ起動
3. certbot の HTTP-01 チャレンジで本物の証明書を取得（`/var/www/certbot` 経由）
4. Nginx をリロードして本物の証明書を読み込む

検証用に Let's Encrypt のステージング環境で先に試したい場合は末尾に `--staging` を付ける:

```bash
./scripts/init-letsencrypt.sh raditomo.hidenv.com you@example.com --staging
```

ステージングで成功確認できたら `infra/nginx/certs` をクリアして本番取得を再実行する。

### 5. 全コンテナ起動

```bash
docker compose up -d
```

起動するもの:

| サービス | 説明 |
|---|---|
| `nginx` | 80/443 を受ける。SPA 配信 + `/api/*` プロキシ + HLS auth_request |
| `app` | Spring Boot（prod プロファイル）、内部 8080 |
| `db` | Postgres 16。Flyway がアプリ起動時にマイグレーション実行 |
| `certbot` | 12 時間ごとに `certbot renew --webroot` を実行（自動更新） |

### 6. 許可ユーザーを登録

このアプリは許可リスト方式。Google アカウントのメールを事前に追加する:

```bash
docker compose run --rm app cli users add you@gmail.com
```

サブコマンド:

```bash
docker compose run --rm app cli users list
docker compose run --rm app cli users remove you@gmail.com
```

### 7. 動作確認

```bash
curl -I https://raditomo.hidenv.com/
# HTTP/2 200 が返り、Strict-Transport-Security ヘッダがあること
```

ブラウザで `https://raditomo.hidenv.com/` にアクセス → ログイン画面 → Google ログイン → 番組表に到達できれば OK。

---

## 運用

### ログ確認

```bash
docker compose logs -f app          # アプリログ（Spring）
docker compose logs -f nginx        # アクセスログ
docker compose logs -f certbot      # 証明書更新ログ
```

`app` のログファイルは `./data/logs/` にも出力される（ローテーションは Spring 側に任せる）。

### バッチ手動実行

CLI から（管理用）:

```bash
docker compose run --rm app cli download                  # F4 → F2 一括
docker compose run --rm app cli download-programs         # F4 のみ
docker compose run --rm app cli download-audio --date 20260424 [--force]
```

Web からは「設定」画面の下部にバッチ手動実行 UI がある。

### スケジューラ

毎朝 5:30 JST に F4 → F2 が自動実行される（`raditomo.scheduler.enabled=true`、prod プロファイルではデフォルト有効）。

### DB バックアップ

```bash
docker compose exec -T db pg_dump -U radiko radiko | gzip > backup-$(date +%Y%m%d).sql.gz
```

cron などで日次実行を推奨。`./data/postgres` ディレクトリを丸ごと落とすバックアップでも可。

### 録音ファイル

`./data/recordings/{userId}/` 配下に MP3 と HLS が格納される。容量逼迫時は古い番組を削除（Web の「ライブラリ」画面から削除可能、履歴は残る）。

---

## 更新フロー

新しいコードをデプロイする手順:

```bash
git pull
cd apps/frontend && npm ci && npm run build && cd ../..
docker compose build app                # 必要な時のみ（バックエンド変更時）
docker compose up -d app nginx          # ローリング再起動
```

DB スキーマ変更は Flyway が自動適用する（マイグレーションファイルは `apps/backend/src/main/resources/db/migration/`）。

---

## 証明書の自動更新

`certbot` コンテナが 12 時間ごとに `certbot renew` を実行する。Let's Encrypt は有効期限 30 日前から更新可能なため、通常は無人で更新が行われる。

更新後は **Nginx をリロードしないと新しい証明書が読み込まれない**点に注意。週次や日次で以下を cron 実行することを推奨:

```bash
0 4 * * * cd /path/to/raditomo && docker compose exec -T nginx nginx -s reload >> /var/log/nginx-reload.log 2>&1
```

更新が走ったかどうかは `docker compose logs certbot --tail 50` で確認できる。

---

## トラブルシュート

### 403 Forbidden が出る

- 許可リストに該当ユーザーがいない可能性。`docker compose run --rm app cli users list` で確認
- JWT がパスのユーザー ID と一致しない（HLS の場合）。再ログインで治ることが多い

### Nginx が起動しない

- 証明書ファイルが見当たらないケースが多い。`infra/nginx/certs/live/raditomo.hidenv.com/` の中身を確認
- 初回は `init-letsencrypt.sh` を経由していないと仮証明書すら無いので失敗する

### Let's Encrypt のレート制限に達した

- 同一ドメインで一週間以内に 5 回失敗するとロックされる
- まず `--staging` で動作検証してから本番取得を行うこと

### radiko の認証や番組取得が失敗する

- ラジコのエリア判定はサーバー外部 IP の地理情報に依存する。VPN・クラウドの IP では聴取不可
- ログに `auth1`/`auth2` 関連のエラーが出ていないか確認
- ラジコ仕様変更（`smartstream.ne.jp` 配信開始）に対応済みか `docs/design/03-radiko-integration.md` を確認

### F2 が大量失敗する

- ffmpeg がインストールされているか（Dockerfile で `apk add ffmpeg` 済みなので通常 OK）
- 並列度が高すぎる場合は `RADIKO_DOWNLOAD_CONCURRENCY` を下げる
- 履歴の `error_message` カラムをチェック

### メールが届かない

- Gmail のアプリパスワードを使っているか（通常パスワードは不可）
- `SMTP_USERNAME` が完全なメールアドレスか
- スパム判定されている可能性も確認

---

## チェックリスト（初回デプロイ）

- [ ] DNS A レコード設定（`raditomo.hidenv.com` → サーバー外部 IP）
- [ ] ルーターのポート転送 80/443
- [ ] Google OAuth クライアントの redirect URI 登録
- [ ] Gmail アプリパスワード発行
- [ ] `.env` 完成（特に `JWT_SECRET` の生成と `APP_BASE_URL` のドメイン）
- [ ] フロントエンドビルド完了（`apps/frontend/dist/index.html` 存在）
- [ ] `init-letsencrypt.sh` 実行成功（`infra/nginx/certs/live/<domain>/fullchain.pem` 存在）
- [ ] `docker compose up -d` で全コンテナ Healthy
- [ ] 許可ユーザー追加（`cli users add`）
- [ ] ブラウザでログイン → 番組表表示まで成功
- [ ] cron に Nginx リロード（証明書更新後の反映用）追加
- [ ] DB バックアップの仕組みを用意
