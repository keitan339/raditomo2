# 更新と日常運用

2回目以降のデプロイ手順と日常運用。初回セットアップは [`01-initial-setup.md`](./01-initial-setup.md) を参照。

## 更新フロー

`main` への push が GitHub Actions でテスト緑 → GHCR にイメージ公開、まで自動。サーバー側はスクリプト1本:

```bash
./deployment/deploy.sh
```

中身は以下と等価（`deployment/` をカレントにして実行される）:

```bash
git -C <repo_root> pull --ff-only   # docker-compose.yml や deploy.sh 自体の更新を反映
docker compose pull app nginx       # GHCR から最新イメージを取得
docker compose up -d app nginx      # ローリング再起動
docker image prune -f               # 古いイメージを掃除
```

DB スキーマ変更は Flyway が自動適用する（マイグレーションファイルはバックエンド image に同梱）。

### CI の動き

`.github/workflows/ci.yml` の `build-and-push` ジョブが `main` への push 時に動作する:

1. backend UT/IT・frontend UT・Playwright スモークが全緑になるのを待つ
2. GHCR にログイン（`GITHUB_TOKEN`）
3. `build/backend/Dockerfile` から `raditomo-backend` を build & push（`:latest` と `:<sha>`）
4. `build/web/Dockerfile` から `raditomo-web` を build & push（同上、nginx テンプレートは build-contexts で渡す）

イメージタグ `:<sha>` は固定参照したい時用（普段は `:latest` で十分）。

### 特定のバージョンへロールバック

`deployment/docker-compose.yml` の `:latest` を `:<コミット SHA>` に書き換えて `deploy.sh` 実行。GHCR には過去の `:<sha>` タグが残っているので任意の時点に戻せる。

---

## 日常運用

### ログ確認

```bash
docker compose logs -f app          # アプリログ（Spring）
docker compose logs -f nginx        # アクセスログ
docker compose logs -f certbot      # 証明書更新ログ
```

`app` のログファイルは `deployment/data/logs/` にも出力される（ローテーションは Spring 側に任せる）。

### バッチ手動実行

`deployment/` ディレクトリでラッパースクリプトから:

```bash
./raditomo download                       # F4 → F2 一括
./raditomo download-programs              # F4 のみ
./raditomo download-audio --date 20260424 [--force]
```

Web からは「設定」画面の下部にバッチ手動実行 UI がある。

### スケジューラ

毎朝 5:30 JST に F4 → F2 が自動実行される（`raditomo.scheduler.enabled=true`、prod プロファイルではデフォルト有効）。

### 許可ユーザー追加・削除

```bash
./raditomo users add another@gmail.com
./raditomo users remove another@gmail.com
./raditomo users list
```

### DB バックアップ

```bash
docker compose exec -T db pg_dump -U radiko radiko | gzip > backup-$(date +%Y%m%d).sql.gz
```

cron などで日次実行を推奨。`deployment/data/postgres/` ディレクトリを丸ごと落とすバックアップでも可。

### 録音ファイル

`deployment/data/recordings/{userId}/` 配下に MP3 と HLS が格納される。容量逼迫時は古い番組を削除（Web の「ライブラリ」画面から削除可能、履歴は残る）。

---

## 証明書の自動更新

`certbot` コンテナが 12 時間ごとに `certbot renew` を実行する。Let's Encrypt は有効期限 30 日前から更新可能なため、通常は無人で更新が行われる。

更新後は **Nginx をリロードしないと新しい証明書が読み込まれない**点に注意。初回セットアップの cron で日次リロードを仕込んでいる前提:

```cron
0 4 * * * cd /path/to/raditomo/deployment && docker compose exec -T nginx nginx -s reload >> /var/log/nginx-reload.log 2>&1
```

更新が走ったかどうかは `docker compose logs certbot --tail 50` で確認できる。

---

## トラブルシュート

### 403 Forbidden が出る

- 許可リストに該当ユーザーがいない可能性。`./raditomo users list` で確認
- JWT がパスのユーザー ID と一致しない（HLS の場合）。再ログインで治ることが多い

### Nginx が起動しない

- 証明書ファイルが見当たらないケースが多い。`deployment/certs/live/raditomo.hidenv.com/` の中身を確認
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

### `docker compose pull` で manifest unknown / unauthorized

- GHCR イメージが public になっていない可能性。github.com/keitan339?tab=packages から `raditomo-backend` / `raditomo-web` の visibility を確認
- 初回 push 後に CI が失敗していて image がまだ無いケースもある。GitHub Actions の最新 run を確認
