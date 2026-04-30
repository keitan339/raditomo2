#!/usr/bin/env bash
# 自宅サーバーで実行するデプロイスクリプト。
#
# 流れ:
#   1. main を git pull（docker-compose.yml や deploy.sh 自体の更新を反映）
#   2. GHCR から最新イメージを pull
#   3. app / nginx をローリング再起動
#   4. 古いイメージを掃除
#
# 前提:
#   - 初回セットアップ完了済み（.env / 証明書 / 許可ユーザーが揃っている）
#   - GHCR は public のため docker login は不要
#
# 使い方:
#   ./scripts/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> git pull"
git pull --ff-only

echo "==> GHCR から最新イメージを pull"
docker compose pull app nginx

echo "==> app / nginx を再起動"
docker compose up -d app nginx

echo "==> 古いイメージを掃除"
docker image prune -f

echo "完了。https://${APP_DOMAIN:-<APP_DOMAIN>} で稼働確認してください。"
