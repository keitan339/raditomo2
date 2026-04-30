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

CLI 実行用（`deployment/` ディレクトリでラッパースクリプト経由）:
```bash
cd deployment
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

### 設定例（実装に対応）

実構成は `infra/nginx/conf.d/default.conf` を参照。要点は以下。

```nginx
# HTTP -> HTTPS（Let's Encrypt の webroot だけ素通し）
server {
    listen 80;
    server_name raditomo.hidenv.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    http2 on;
    server_name raditomo.hidenv.com;

    ssl_certificate     /etc/nginx/certs/live/raditomo.hidenv.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/live/raditomo.hidenv.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer" always;

    # SPA
    root /usr/share/nginx/html;
    location / {
        try_files $uri /index.html;
    }

    # API プロキシ。/api/internal/** は外部公開しない（auth_request 専用）
    location ~ ^/api/internal/ { return 404; }
    location /api/ {
        proxy_pass http://app:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # HLS。/hls/{userId}/{rest...} → /data/recordings/{userId}/hls/{rest...}
    # 毎リクエスト auth_request で /api/internal/auth-hls に Authorization を転送し、
    # JWT の sub とパスの userId 一致を Spring 側で検証する。
    location ~ ^/hls/(\d+)/(.+)$ {
        auth_request /__internal_auth_hls;
        alias /data/recordings/$1/hls/$2;
        add_header Cache-Control "private, no-store" always;
        types {
            application/vnd.apple.mpegurl m3u8;
            video/mp2t                    ts;
        }
    }

    location = /__internal_auth_hls {
        internal;
        proxy_pass              http://app:8080/api/internal/auth-hls;
        proxy_pass_request_body off;
        proxy_set_header        Content-Length "";
        proxy_set_header        X-Original-URI $request_uri;
        proxy_set_header        Authorization  $http_authorization;
    }
}
```

### HLSパス変換

`/hls/{userId}/{title}/{date}/playlist.m3u8` を `/data/recordings/{userId}/hls/{title}/{date}/playlist.m3u8` にマッピングする regex location で対応する（上記 `location ~ ^/hls/(\d+)/(.+)$` 部分）。userId 部分は `\d+` に限定して、JWT の sub と数値比較できるようにしている。

---

## セキュリティ設計

### 認証フロー（再掲）

1. Google OAuth 2.0 で ID トークン取得（本番）/ Keycloak 認可コードフロー（ローカル）
2. バックエンドで `email_verified=true` かつ許可リスト内（`users.is_active=true`）を確認
3. JWT（アクセストークン、24h）を発行（リフレッシュトークンは発行しない）
4. SPAは `Authorization: Bearer <accessToken>` で API 呼び出し
5. HLS 再生中も hls.js の `xhrSetup` で同じヘッダを毎リクエスト付与

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
- リフレッシュトークン Cookie は採用しないため、CSRF トークンも不要

### HLS 配信のアクセス制御

#### 方式選定（実装での結論）

| 方式 | メリット | デメリット | 採否 |
|------|---------|-----------|------|
| Nginx auth_request + JWT ヘッダ毎送信 | playlist/segment いずれも同じ JWT で認可。実装シンプル。 | リクエストごとに Spring へサブリクエスト（個人利用なら無視できる） | **採用** |
| 署名付きURL（time-limited） | 認証 1 回で済む | playlist 内の .ts は相対 URL で署名が乗らない → m3u8 の書き換えか sub_filter が必要 | 不採用 |
| Cookie 認証 + auth_request | セッション中は1回 Cookie 発行 | Cookie 管理・SameSite/HttpOnly 制御が増える | 不採用 |

→ **採用: `auth_request` 方式 + フロント `xhrSetup` で `Authorization: Bearer <jwt>` を毎リクエスト付与**

ラジコ録音は1ユーザーが順次再生する程度の負荷で、auth_request のサブリクエスト増加コストは無視できる。設計シンプル化を優先した。

#### 実装

1. `GET /api/recordings/{historyId}` のレスポンスは `hlsUrl: "/hls/{userId}/{title}/{date}/playlist.m3u8"` を返す（署名なし）
2. SPA は hls.js の `xhrSetup` で全 XHR に `Authorization: Bearer <accessToken>` を付与する
   ```ts
   new Hls({
     xhrSetup: (xhr) => {
       const token = getAccessToken();
       if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
     },
   });
   ```
3. Nginx の `/hls/...` location は `auth_request /__internal_auth_hls;` で Spring の `/api/internal/auth-hls` にサブリクエスト
4. Spring の `AuthHlsController` は:
   - `Authorization` ヘッダから JWT を検証（既存の `JwtAuthenticationFilter` が `@AuthenticationPrincipal Long userId` を埋める）
   - `X-Original-URI` から `/hls/{userId}/...` の数値部分を抽出
   - JWT の sub と一致 → 200 / 不一致 → 403 / JWT 不在 or 不正 → 401
5. `/api/internal/**` は外部から到達できないように Nginx 側で `return 404;` する（Nginx 内部のサブリクエスト専用）

#### 制限事項

- Safari ネイティブの `<audio src="hls">` は XHR をフックできないため Authorization ヘッダを乗せられない。Safari 対応が必要になった時点で「短命 Cookie 発行 → auth_request が Cookie を見る」ハイブリッドに切り替える方針

---

## CSRF 対策の実装方針

- 全 API は JWT Bearer 認証（Authorization ヘッダ）のため CSRF 対策不要
- リフレッシュトークン Cookie / セッション Cookie は使用しない
- HLS の `auth_request` も同じ Authorization ヘッダを転送するため Cookie ベース攻撃の余地はない

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
- **証明書自動更新**: `docker-compose.yml` に `certbot` サービスを常駐（12 時間ごとに `certbot renew --webroot` を実行、更新があれば証明書ファイルが置き換わる）
- 初回証明書取得は `./deployment/init-letsencrypt.sh <domain> <email>` を使用。仮の自己署名証明書 → Nginx 起動 → certbot で本物取得 → reload までを自動化
