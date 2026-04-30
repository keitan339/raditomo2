#!/usr/bin/env bash
# Let's Encrypt 証明書を初回取得するスクリプト。
#
# 前提:
#   - DNS が raditomo.hidenv.com → サーバー外部 IP に設定されている
#   - ルーターで 80/443 がサーバーに転送されている
#   - .env で APP_DOMAIN / APP_BASE_URL / GOOGLE_OAUTH_REDIRECT_URI が本番ドメインに設定されている
#   - GHCR の raditomo-web イメージが既に CI で push されている（main に push 後）
#
# 流れ:
#   1. 仮の自己署名証明書を生成（Nginx を起動するため、初回のみ）
#   2. nginx だけ起動して HTTP-01 チャレンジ用ディレクトリを公開
#   3. 既存の certs/live/<domain>/ を削除（certbot の "live directory exists" を回避）
#      nginx は既にメモリに cert を読み込んでいるので削除しても稼働継続
#   4. certbot で本物の証明書を取得（webroot 認証）
#   5. nginx をリロードして新しい証明書を読み込む
#
# staging → 本番への切り替えや再取得もこのスクリプトで可能。
# ただし既に Let's Encrypt 証明書が取得済みのときに再実行すると
# レート制限を消費する点に注意（通常は certbot コンテナの自動更新に任せる）。
#
# 使い方（deployment/ ディレクトリで実行）:
#   ./init-letsencrypt.sh raditomo.hidenv.com you@example.com [--staging]
set -euo pipefail

# このスクリプトのあるディレクトリ（= deployment/）を作業ディレクトリにする
cd "$(dirname "$0")"

DOMAIN="${1:?usage: $0 <domain> <email> [--staging]}"
EMAIL="${2:?usage: $0 <domain> <email> [--staging]}"
EXTRA_ARGS=()
if [ "${3-}" = "--staging" ]; then
  EXTRA_ARGS+=("--staging")
fi

# cert / webroot ディレクトリを certbot コンテナ経由で準備する。
# certbot は root 実行のため、bind mount 先のホストパスが root 所有でも書き込める。
# 仮の自己署名証明書も同コンテナで生成（nginx の初回起動に必要）。
echo "==> ディレクトリ準備と仮の自己署名証明書を生成（certbot コンテナ経由）"
docker compose run --rm --entrypoint sh certbot -c "
  mkdir -p /var/www/certbot /etc/letsencrypt/live/$DOMAIN
  if [ ! -f /etc/letsencrypt/live/$DOMAIN/fullchain.pem ]; then
    echo '   仮の自己署名証明書を生成中'
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
      -keyout /etc/letsencrypt/live/$DOMAIN/privkey.pem \
      -out /etc/letsencrypt/live/$DOMAIN/fullchain.pem \
      -subj /CN=$DOMAIN
  else
    echo '   既存の cert があるのでスキップ'
  fi
"

# nginx を起動（既に restart-loop 中なら、上で生成した仮 cert を読んで起動成功する）
echo "==> Nginx を起動 / 再起動"
docker compose up -d nginx
docker compose restart nginx

# certbot は live/<domain>/ が既に存在するとエラー終了するため、ここで削除する。
# certbot が以前生成した cert ファイルは root 所有なのでホスト側 rm では消せない。
# 同じ certbot コンテナ（root）を使って削除する。
# nginx は起動時に cert を in-memory にロード済みなので、ファイルを消しても
# reload するまで古い cert で稼働を続ける（証明書配信は停止しない）。
echo "==> 既存の証明書ディレクトリを削除（certbot コンテナ経由）"
docker compose run --rm --entrypoint "" certbot \
  rm -rf "/etc/letsencrypt/live/$DOMAIN" \
         "/etc/letsencrypt/archive/$DOMAIN" \
         "/etc/letsencrypt/renewal/$DOMAIN.conf"

echo "==> certbot で本番証明書を取得"
docker compose run --rm --entrypoint "" certbot \
  certbot certonly \
    --webroot -w /var/www/certbot \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos --non-interactive \
    "${EXTRA_ARGS[@]}"

echo "==> Nginx をリロード"
docker compose exec nginx nginx -s reload

echo "完了。https://$DOMAIN で稼働確認してください。"
