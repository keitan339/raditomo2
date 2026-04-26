# Docker構成・セキュリティ設計

## Docker Compose 構成

### 3コンテナ構成

```yaml
# docker-compose.yml（概略）
services:
  nginx:
    image: nginx:1.27-alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/certs:/etc/nginx/certs:ro
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./data/recordings:/data/recordings:ro   # HLS配信用
    depends_on:
      - app
    networks:
      - radiko-net

  app:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/radiko
      SPRING_DATASOURCE_USERNAME: radiko
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      RECORDINGS_BASE_PATH: /data/recordings
      XML_BASE_PATH: /data/xml
      LOG_BASE_PATH: /var/log/app
      JWT_SECRET: ${JWT_SECRET}
      GOOGLE_OAUTH_CLIENT_ID: ${GOOGLE_OAUTH_CLIENT_ID}
      GOOGLE_OAUTH_CLIENT_SECRET: ${GOOGLE_OAUTH_CLIENT_SECRET}
      GOOGLE_OAUTH_REDIRECT_URI: https://raditomo.hidenv.com/auth/callback
      SMTP_HOST: smtp.gmail.com
      SMTP_PORT: 587
      SMTP_USERNAME: ${SMTP_USERNAME}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      RADIKO_DOWNLOAD_CONCURRENCY: 8
      TZ: Asia/Tokyo
    volumes:
      - ./data/recordings:/data/recordings
      - ./data/xml:/data/xml
      - ./data/logs:/var/log/app
    depends_on:
      - db
    networks:
      - radiko-net

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: radiko
      POSTGRES_USER: radiko
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      TZ: Asia/Tokyo
      PGTZ: Asia/Tokyo
    volumes:
      - ./data/postgres:/var/lib/postgresql/data
    networks:
      - radiko-net

networks:
  radiko-net:
    driver: bridge
```

### ポイント
- **HTTPS**: Nginx でSSL終端。Let's Encrypt の証明書を `./nginx/certs` にマウント（certbot は別途運用 or `certbot/certbot` コンテナを追加）
- **ボリュームマウント**:
  - `./data/recordings` は app（読み書き）と nginx（読み専用）の両方にマウント
  - `./data/xml`, `./data/logs`, `./data/postgres` は app/db のみ
- **環境変数**: `.env` ファイルから読み込み（リポジトリにはコミットしない、`.env.example` を提供）
- **ネットワーク**: 専用 bridge ネットワーク。Nginx のみ外部公開

### App Dockerfile（概略）

```dockerfile
FROM eclipse-temurin:21-jre-alpine

# ffmpeg インストール
RUN apk add --no-cache ffmpeg tzdata
ENV TZ=Asia/Tokyo

WORKDIR /app
COPY target/radiko-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

CLI 実行用（ホストのラッパースクリプト経由）:
```bash
./raditomo download                       # F4→F2 一括
./raditomo download-programs              # F4 単体
./raditomo download-audio --date 20260424 # F2 単体
./raditomo users add user@example.com     # 許可リスト追加
```

---

## Nginx 設定

### 構成方針

- HTTPS強制（HTTPは443にリダイレクト）
- `/` → React SPA配信（fallback to index.html）
- `/api/*` → Spring Boot プロキシ
- `/hls/*` → 録音ファイル直配信（認証必須）

### 設定例（概略）

```nginx
server {
    listen 80;
    server_name raditomo.hidenv.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name raditomo.hidenv.com;

    ssl_certificate /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    # SPA
    root /usr/share/nginx/html;
    location / {
        try_files $uri /index.html;
    }

    # API プロキシ
    location /api/ {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # HLS配信（認証付き）
    location /hls/ {
        # バックエンドに認証問い合わせ
        auth_request /internal/auth-hls;
        auth_request_set $auth_user $upstream_http_x_auth_user;

        # ユーザーIDがパスと一致するかは Spring 側で検証済み
        alias /data/recordings/;
        # /hls/{userId}/.../playlist.m3u8 を /data/recordings/{userId}/hls/.../playlist.m3u8 にマッピング

        # CORS不要（同一オリジン）
        add_header Cache-Control "private, no-cache";
    }

    location = /internal/auth-hls {
        internal;
        proxy_pass http://app:8080/api/internal/auth-hls;
        proxy_pass_request_body off;
        proxy_set_header Content-Length "";
        proxy_set_header X-Original-URI $request_uri;
        proxy_set_header Authorization $http_authorization;
        # またはクエリパラメータの token を Authorization に変換
    }
}
```

### HLSパス変換

実際は alias の単純マッピングではなく、`/hls/{userId}/{title}/{date}/playlist.m3u8` を `/data/recordings/{userId}/hls/{title}/{date}/playlist.m3u8` に変換する必要がある。
→ `location ~ ^/hls/([^/]+)/(.+)$ { alias /data/recordings/$1/hls/$2; }` で対応

---

## セキュリティ設計

### 認証フロー（再掲）

1. Google OAuth 2.0 で ID トークン取得
2. バックエンドで `email_verified=true` かつ許可リスト内（`users.is_active=true`）を確認
3. JWT（アクセストークン）+ リフレッシュトークンを発行
4. SPAは `Authorization: Bearer <accessToken>` で API 呼び出し

### JWT 設計

| 項目 | 値 | 備考 |
|------|------|------|
| 署名アルゴリズム | HS256 | 個人利用、シンプル |
| シークレット | 環境変数 `JWT_SECRET`（256bit以上ランダム） | |
| アクセストークン有効期限 | **24時間** | ブラウザセッション中をカバー |
| リフレッシュトークン | **なし** | 自動延長は行わない（毎回認証） |
| クレーム | `sub`(userId), `email`, `iat`, `exp` | |

### 認証ポリシー

- リフレッシュトークンは **発行しない**
- アクセストークン期限切れ時、または新しいブラウザセッションでは Google OAuth による再ログインを必須とする
- Google 側はアプリ認可済みのため、通常は同意画面スキップで1クリック程度で再ログイン可能

### トークン保存場所（フロント）

- アクセストークン: **SessionStorage**
  - タブ/ブラウザを閉じると自動的に消える
  - localStorage は XSS 攻撃に弱いため使わない
  - 別タブでは再ログインが必要（SessionStorage はタブ単位）
- Cookie は使用しない（リフレッシュトークンがないため）

### ログアウト

- フロント: SessionStorage からトークン削除 → ログイン画面へリダイレクト
- バックエンド: 特に状態管理不要（JWT はステートレス・有効期限のみで管理）
  - ただし「サーバー側の即時失効」が必要なら、`token_blacklist` テーブルを追加して `jti` ベースで失効管理（オプション）

### CORS

- 同一オリジン（Nginx 経由）のため不要
- 開発時のみ `localhost:5173` (Vite) → `localhost:8080` でCORSが必要 → dev profile でのみ許可

### CSRF

- JWT を Authorization ヘッダーで送る限り CSRF 対策は不要
- ただし `/api/auth/refresh` がリフレッシュトークン Cookie を使う場合は CSRF トークン必要 → リフレッシュエンドポイントは double-submit cookie パターンを採用

### HLS 配信のアクセス制御

#### 方式選定

| 方式 | メリット | デメリット | 採用 |
|------|---------|-----------|------|
| Nginx auth_request | リアルタイム認証 | リクエストごとにバックエンド呼び出し（HLSはセグメントごとなので頻度高） | △ |
| 署名付きURL（time-limited） | バックエンド負荷軽い | URL有効期限管理 | ○ |
| Cookie認証 + auth_request | セッション中は1回認証 | Cookie管理 | △ |

→ **採用: 署名付きURLで playlist.m3u8 のみ保護、セグメント (.ts) はパス推測困難なディレクトリで保護**

##### 実装案

1. SPA から `GET /api/recordings/{historyId}` 取得時に、サーバーが署名付きHLS URL を返す
   - 例: `/hls/<token>/<userId>/<title>/<date>/playlist.m3u8`
   - `<token>` は JWT 風（HMAC署名）で `userId, historyId, expires` を含む（有効期限 1時間）
2. Nginx は `/hls/{token}/...` を受け、auth_request で Spring Boot に検証依頼
3. Spring Boot が token を検証し、200 OK or 401
4. .ts セグメントのURLも playlist.m3u8 内で同じ token プレフィックスにする
   → ffmpeg 出力の playlist.m3u8 を取得時に書き換える（または Nginx の sub_filter で書き換え）

これで:
- 認証は HLS リクエスト全体で1セットの token 検証
- 期限切れ後は再生不可（再生継続には API 再取得が必要）

---

## CSRF 対策の実装方針

- メインAPIはJWT Bearer なので CSRF 対策不要
- リフレッシュエンドポイント（Cookie使用）は CSRF トークン送信を必須化:
  - SPA起動時に `GET /api/auth/csrf` で `Set-Cookie: XSRF-TOKEN=...` を発行
  - SPAは Cookie値を `X-XSRF-TOKEN` ヘッダーに乗せて送信
  - Spring Security の標準 `CookieCsrfTokenRepository.withHttpOnlyFalse()` で対応

---

## セキュリティ強化（追加）

| 項目 | 対策 |
|------|------|
| HTTPS強制 | Nginxで HTTP→HTTPS リダイレクト + HSTS ヘッダー |
| XSS | React 標準のエスケープに依存。`dangerouslySetInnerHTML` は使わない |
| SQLインジェクション | Spring Data JPA / JdbcTemplate のパラメータバインドのみ使用 |
| 依存ライブラリ脆弱性 | `mvn dependency-check` を CI で実行（将来） |
| シークレット管理 | `.env` をリポジトリ除外。`.env.example` のみコミット |
| パスワードレス | DB は内部ネットワークのみアクセス可。外部公開はNginxのみ |
| ログのPIIマスク | メールアドレス等は INFO以上では出さない or 部分マスク |

---

## 環境変数一覧（`.env.example`）

```bash
# DB
DB_PASSWORD=changeme

# JWT
JWT_SECRET=changeme_random_256bit_hex_string

# Google OAuth
GOOGLE_OAUTH_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=...
GOOGLE_OAUTH_REDIRECT_URI=https://raditomo.hidenv.com/auth/callback

# SMTP
SMTP_USERNAME=sender@gmail.com
SMTP_PASSWORD=app_password_here

# Radiko
RADIKO_DOWNLOAD_CONCURRENCY=8

# Domain
APP_BASE_URL=https://raditomo.hidenv.com
```

---

## 公開構成

```
[Internet]
   │
   ▼ HTTPS
[Router/家庭用ルータ ポート転送 443→自宅サーバー]
   │
   ▼
[自宅サーバー]
   ├─ Docker (nginx:443) → 公開
   ├─ Docker (app:8080)  → 内部のみ
   └─ Docker (db:5432)   → 内部のみ
```

- ドメイン: `raditomo.hidenv.com`（Value Domain で管理）
- Let's Encrypt の **HTTP-01 チャレンジ**で証明書取得（443/80 ポート公開が前提）
- ルーターのポート転送設定が必要（443・80）
- 証明書自動更新は `certbot/certbot` コンテナを別途追加 or ホストの cron + certbot で運用
