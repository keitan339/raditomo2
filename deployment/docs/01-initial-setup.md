# 初回デプロイ手順

自宅サーバーへの初回デプロイ手順書。`raditomo.hidenv.com` で公開する想定。
2回目以降の更新フローは [`02-update.md`](./02-update.md) を参照。

## 全体像

イメージは GitHub Actions が GHCR にビルド・公開する。自宅サーバーはそれを pull して動かすだけ。

```
[ローカル開発] → git push main → GitHub Actions (テスト+ビルド+GHCR push)
                                            ↓
                          [自宅] ./deployment/deploy.sh で pull & up -d
```

サーバー上の操作は **すべて `deployment/` 配下で完結**する設計。`apps/`（ソース）と `build/`（Dockerfile / nginx テンプレート）はランタイム不要。

GHCR に置かれるイメージ:

| イメージ | 中身 |
|---|---|
| `ghcr.io/keitan339/raditomo-backend:latest` | Spring Boot JAR + ffmpeg + JRE |
| `ghcr.io/keitan339/raditomo-web:latest` | Nginx + frontend dist + nginx 設定（テンプレート展開） |

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
| GHCR イメージ | `main` への push 後、CI で publish 済み + `raditomo-backend` / `raditomo-web` が **public** に設定されている |

DNS とルーターの設定は **Let's Encrypt の HTTP-01 チャレンジで証明書取得する前**に必須。先に整えておく。

---

## セットアップ手順

### 1. リポジトリ取得

```bash
git clone <repo-url> raditomo
cd raditomo/deployment
```

以降の手順はすべて `deployment/` 配下で実行する。

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
| `APP_DOMAIN` | `raditomo.hidenv.com`（スキーム無し。Nginx の `server_name` と証明書パスに使用） |
| `APP_BASE_URL` | `https://raditomo.hidenv.com`（スキーム付き。バックエンドの OAuth リダイレクト等に使用） |
| `RADIKO_DOWNLOAD_CONCURRENCY` | 並列度。デフォルト 8 |

`.env` は **絶対にリポジトリにコミットしない**（`.gitignore` で除外済み）。

### 3. Let's Encrypt 証明書を取得

DNS とルーター設定が済んでいることを確認してから:

```bash
./init-letsencrypt.sh raditomo.hidenv.com you@example.com
```

スクリプトは以下を自動で行う:

1. 仮の自己署名証明書を生成（Nginx を起動するため）
2. Nginx だけ起動
3. certbot の HTTP-01 チャレンジで本物の証明書を取得（`/var/www/certbot` 経由）
4. Nginx をリロードして本物の証明書を読み込む

検証用に Let's Encrypt のステージング環境で先に試したい場合は末尾に `--staging` を付ける:

```bash
./init-letsencrypt.sh raditomo.hidenv.com you@example.com --staging
```

ステージングで成功確認できたら `deployment/certs/` をクリアして本番取得を再実行する。

### 4. 全コンテナ起動

```bash
docker compose pull
docker compose up -d
```

`pull` で GHCR から `raditomo-backend` / `raditomo-web` の最新イメージを取得する（GHCR は public のため認証不要）。`db` と `certbot` は Docker Hub の公式イメージ。

起動するもの:

| サービス | 説明 |
|---|---|
| `nginx` | 80/443 を受ける。SPA 配信 + `/api/*` プロキシ + HLS auth_request（設定は image に焼き込み済み、`APP_DOMAIN` を起動時展開） |
| `app` | Spring Boot（prod プロファイル）、内部 8080 |
| `db` | Postgres 16。Flyway がアプリ起動時にマイグレーション実行 |
| `certbot` | 12 時間ごとに `certbot renew --webroot` を実行（自動更新） |

### 5. 許可ユーザーを登録

このアプリは許可リスト方式。Google アカウントのメールを事前に追加する:

```bash
docker compose run --rm app cli users add you@gmail.com
```

サブコマンド:

```bash
docker compose run --rm app cli users list
docker compose run --rm app cli users remove you@gmail.com
```

### 6. 動作確認

```bash
curl -I https://raditomo.hidenv.com/
# HTTP/2 200 が返り、Strict-Transport-Security ヘッダがあること
```

ブラウザで `https://raditomo.hidenv.com/` にアクセス → ログイン画面 → Google ログイン → 番組表に到達できれば OK。

### 7. cron 設定（推奨）

証明書更新後の Nginx リロード:

```bash
0 4 * * * cd /path/to/raditomo/deployment && docker compose exec -T nginx nginx -s reload >> /var/log/nginx-reload.log 2>&1
```

DB バックアップ:

```bash
0 3 * * * cd /path/to/raditomo/deployment && docker compose exec -T db pg_dump -U radiko radiko | gzip > /path/to/backups/raditomo-$(date +\%Y\%m\%d).sql.gz
```

---

## 初回デプロイ チェックリスト

- [ ] DNS A レコード設定（`raditomo.hidenv.com` → サーバー外部 IP）
- [ ] ルーターのポート転送 80/443
- [ ] Google OAuth クライアントの redirect URI 登録
- [ ] Gmail アプリパスワード発行
- [ ] GHCR イメージを **public** に設定（github.com/keitan339?tab=packages）
- [ ] `deployment/.env` 完成（特に `JWT_SECRET` の生成、`APP_DOMAIN` / `APP_BASE_URL` のドメイン）
- [ ] `init-letsencrypt.sh` 実行成功（`deployment/certs/live/<domain>/fullchain.pem` 存在）
- [ ] `docker compose pull && docker compose up -d` で全コンテナ Healthy
- [ ] 許可ユーザー追加（`cli users add`）
- [ ] ブラウザでログイン → 番組表表示まで成功
- [ ] cron に Nginx リロード（証明書更新後の反映用）追加
- [ ] cron に DB バックアップ追加
