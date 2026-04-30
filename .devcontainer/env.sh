#!/bin/bash

# このスクリプトが存在するディレクトリに移動する
cd "$(dirname "$0")"

source ~/.keychain/AeroSlim-sh

# .envファイルのパス
ENV_FILE=".env"

# .envファイルを初期化（すでにあれば上書き）
> "${ENV_FILE}"

# プロジェクト名の取得
PROJECT_NAME=$(basename $(realpath $(pwd)/..))

# GitHubCliの設定ファイル共有
GIT_HUB_CONFIG_DIRECTORY=~/.config/gh

# 展開する環境変数を書き込み
echo "GIT_HUB_CONFIG_DIRECTORY=${GIT_HUB_CONFIG_DIRECTORY}" >> "${ENV_FILE}"
echo "HOST_UID=$(id -u)" >> "${ENV_FILE}"
echo "HOST_GID=$(id -g)" >> "${ENV_FILE}"
echo "HOST_DOCKER_GID=$(grep '^docker:' /etc/group | cut -d: -f3)" >> "${ENV_FILE}"
echo "GIT_USER_NAME=$(git config user.name)" >> "${ENV_FILE}"
echo "GIT_USER_EMAIL=$(git config user.email)" >> "${ENV_FILE}"
echo "SSH_AUTH_SOCK=$(echo $SSH_AUTH_SOCK)" >> "${ENV_FILE}"
echo "COMPOSE_PROJECT_NAME=$(echo $PROJECT_NAME)" >> "${ENV_FILE}"
