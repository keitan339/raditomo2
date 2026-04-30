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
#   1. 仮の自己署名証明書を生成（Nginx を起動するため）
#   2. nginx だけ起動して HTTP-01 チャレンジ用ディレクトリを公開
#   3. certbot で本物の証明書を取得（webroot 認証）
#   4. nginx をリロードして本物の証明書を読み込む
#
# 使い方:
#   ./scripts/init-letsencrypt.sh raditomo.hidenv.com you@example.com [--staging]
set -euo pipefail

DOMAIN="${1:?usage: $0 <domain> <email> [--staging]}"
EMAIL="${2:?usage: $0 <domain> <email> [--staging]}"
EXTRA_ARGS=()
if [ "${3-}" = "--staging" ]; then
  EXTRA_ARGS+=("--staging")
fi

CERT_DIR="./infra/nginx/certs"
WEBROOT="./data/certbot/www"

mkdir -p "$CERT_DIR/live/$DOMAIN" "$WEBROOT"

if [ ! -f "$CERT_DIR/live/$DOMAIN/fullchain.pem" ]; then
  echo "==> 仮の自己署名証明書を生成"
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout "$CERT_DIR/live/$DOMAIN/privkey.pem" \
    -out    "$CERT_DIR/live/$DOMAIN/fullchain.pem" \
    -subj   "/CN=$DOMAIN"
fi

echo "==> Nginx を起動"
docker compose up -d nginx

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
