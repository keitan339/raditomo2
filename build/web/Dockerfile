# Raditomo Web イメージ
# - フロントエンド (Vite ビルド成果物) を Nginx に焼き込む
# - Nginx 設定はテンプレート方式で APP_DOMAIN を起動時に展開
#
# ビルドコンテキストは apps/frontend/。
# nginx テンプレートは Buildx の名前付き build-context (`nginx-templates`) で渡す。
# ローカルで手動ビルドする場合:
#   docker buildx build \
#     --build-context nginx-templates=build/web/templates \
#     -f build/web/Dockerfile \
#     -t raditomo-web:local \
#     apps/frontend

FROM node:22-alpine AS frontend-builder
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=frontend-builder /build/dist /usr/share/nginx/html
COPY --from=nginx-templates / /etc/nginx/templates/
# nginx 公式イメージは /etc/nginx/templates/*.template を envsubst で展開し
# /etc/nginx/conf.d/ に出力する。FILTER で APP_ 接頭辞のみ置換し、
# nginx 内蔵変数 ($host, $remote_addr 等) との衝突を防ぐ。
ENV NGINX_ENVSUBST_FILTER=^APP_
