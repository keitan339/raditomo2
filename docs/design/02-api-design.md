# API設計

## 設計方針

- REST API（JSON）を `/api/*` 配下で提供する
- 認証は `Authorization: Bearer <JWT>` ヘッダー方式（SPAは SessionStorage に保存）
- 同一オリジン（Nginx 経由）配信のため CORS 設定は不要
- 日時は ISO 8601（JST、`+09:00` 付き）で送受信する
- エラーレスポンスは RFC 7807 風の構造で統一する

## 共通

### 認証
- `Authorization: Bearer <accessToken>` を全APIで必須（認証API除く）
- 未認証時: `401 Unauthorized`
- 許可リスト外ユーザー: `403 Forbidden`

### エラーレスポンス
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "broadcastStartAt is required",
  "instance": "/api/registrations"
}
```

### ページネーション
クエリ: `?page=0&size=50`、レスポンス: `{ content: [...], page, size, totalElements, totalPages }`

---

## エンドポイント一覧

### 認証 (F5)

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/auth/google/login-url` | Google OAuth 認可URLを発行（state も発行） |
| GET | `/api/auth/google/callback?code=&state=` | フロント `/auth/callback` 経由で呼ばれる。許可リスト確認＋JWT発行 |
| POST | `/api/auth/logout` | クライアント側でトークン破棄（サーバー側は特になし） |
| GET | `/api/auth/me` | 現在のユーザー情報取得 |

リフレッシュトークンは発行しない（毎回認証ポリシー）。アクセストークン期限切れ・ブラウザセッション切れ時は再ログイン。

#### `/api/auth/google/callback` レスポンス
```json
{
  "accessToken": "eyJhbGc...",
  "expiresIn": 86400,
  "user": { "id": 1, "email": "...", "name": "...", "pictureUrl": "..." }
}
```

---

### エリア・放送局・設定

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/areas` | 全エリア一覧 |
| GET | `/api/stations?areaId=JP13` | エリアの放送局一覧 |
| GET | `/api/users/me/settings` | 自分の設定（current_area_id 等） |
| PUT | `/api/users/me/settings` | エリア変更（変更時は番組表の即時取得を非同期キック） |
| GET | `/api/users/me/station-visibility?areaId=JP13` | 放送局表示/非表示設定の取得 |
| PUT | `/api/users/me/station-visibility/{stationId}` | 表示/非表示の切替 (`{ "isVisible": false }`) |

#### `PUT /api/users/me/settings` レスポンス
```json
{
  "currentAreaId": "JP27",
  "areaChangeFetchStatus": {
    "batchExecutionId": 12345,
    "status": "RUNNING"
  }
}
```

#### エリア変更時の挙動（要件 F1）

1. リクエストを受けたら **まず `user_settings.current_area_id` を新エリアで保存・コミット**（DB変更を確定）
2. その後、新エリア対象の F4 を非同期キック（`triggeredBy = WEB_AREA_CHANGE`、F2 連鎖なし）
3. レスポンスは即時返す。`batchExecutionId` でフロントが進捗確認

**取得失敗時の挙動**:
- F4 がエラーで失敗しても、ステップ1のエリア変更は既にコミット済み → 設定保存は維持される
- 番組表は **次回 F4 実行時に補完**（毎朝5:30 の自動実行 or 手動再実行）
- フロントは `GET /api/batch/executions/{id}` で `status='FAILED'` を検知してエラーメッセージを表示

---

### 番組表 (F1)

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/programs?areaId=JP13&date=20260424` | 指定エリア・指定放送日(5:00区切り)の番組表 |
| GET | `/api/programs/search?q=キーワード&areaId=JP13` | 番組タイトル＋出演者の横断検索 |
| GET | `/api/programs/{programId}` | 番組詳細（登録済みかどうかも含む） |

#### レスポンス例
```json
{
  "broadcastDate": "2026-04-24",
  "stations": [
    {
      "stationId": "TBS",
      "name": "TBSラジオ",
      "programs": [
        {
          "id": 9876,
          "title": "番組名",
          "performers": "...",
          "broadcastStartAt": "2026-04-24T05:00:00+09:00",
          "broadcastEndAt": "2026-04-24T08:00:00+09:00",
          "isPast": true,
          "isWithinTimefreeWindow": true,
          "registration": {
            "registrationId": 100,
            "type": "WEEKLY"
          }
        }
      ]
    }
  ]
}
```

---

### ダウンロード登録 (F1)

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/registrations` | 登録一覧（曜日別グループ化はフロント側） |
| POST | `/api/registrations` | 新規登録 |
| DELETE | `/api/registrations/{id}` | 登録解除（ファイル・履歴は削除しない） |

#### POST リクエスト
```json
{
  "stationId": "TBS",
  "title": "番組名",
  "broadcastStartAt": "2026-04-24T01:00:00+09:00",
  "broadcastEndAt": "2026-04-24T03:00:00+09:00",
  "registrationType": "WEEKLY"
}
```
- `WEEKLY` の場合、サーバー側で `day_of_week` を `broadcastStartAt`（5:00区切り基準）から算出
- 重複登録は `409 Conflict`

---

### ダウンロード履歴 (F1)

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/histories?status=&page=&size=` | 履歴一覧（status 未指定なら全件） |
| DELETE | `/api/histories/{id}` | 履歴削除（次回 F2 で再 DL 対象に戻る） |
| GET | `/api/histories/badge` | 失敗・期限切れの件数バッジ用 (`{ "failedCount": 3, "expiredCount": 1 }`) |

#### `DELETE /api/histories/{id}` 確認警告
- 期限切れ履歴の削除はクライアント側で警告を出す（API側はブロックしない）
- 削除前に `GET /api/histories/{id}` で履歴情報を取得し、`status` と「タイムフリー期限内かどうか」をフロントで判定して警告文言を切替
- フロントの確認ダイアログ文言（要件 F1 通り）:
  - **期限切れ番組**（`status='EXPIRED'` または `status='SUCCESS'` で期限超過）: 「**この番組は再ダウンロードできません**。履歴を削除しますか？」
  - **期限内の成功**（`status='SUCCESS'` で期限内）: 「履歴を削除すると次回バッチで再ダウンロード対象になります。削除しますか？」
  - **失敗**（`status='FAILED'`）: 「履歴を削除すると次回バッチで再ダウンロード対象になります。削除しますか？」

---

### 録音ライブラリ (F3)

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/recordings/groups` | 番組名グルーピング表示用（`{ title, count, latestBroadcastAt }[]`） |
| GET | `/api/recordings?title=...&sort=broadcastAt,desc` | 番組名指定で各回一覧 |
| GET | `/api/recordings/{historyId}` | 録音ファイル詳細 |
| DELETE | `/api/recordings/{historyId}` | 録音ファイル削除（MP3+HLS 両方）。履歴は残す |
| GET | `/api/recordings/{historyId}/playback-position` | 再生位置取得 |
| PUT | `/api/recordings/{historyId}/playback-position` | 再生位置保存 (`{ "positionSeconds": 1234 }`) |

#### HLS ストリーミングURL
- レスポンスに `hlsUrl: "/hls/{userId}/{title}/{date}/playlist.m3u8?token=..."` を含める
- 配信は Nginx 直配信（後述のセキュリティ設計を参照）

---

### バッチ手動実行 (F1)

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/batch/run` | F4 / F2 / F4_F2 を非同期で実行 |
| GET | `/api/batch/executions?status=RUNNING` | 実行中バッチの一覧 |
| GET | `/api/batch/executions/{id}` | 個別バッチの状態詳細 |

#### POST リクエスト
```json
{
  "type": "F4_F2",
  "options": {
    "date": "20260424",
    "force": false
  }
}
```
- `type=F4` の場合 `options` は無視
- 既に同種バッチが RUNNING 中なら `409 Conflict`
- レスポンス: `{ "batchExecutionId": 12345, "status": "RUNNING" }`

---

## 認証フロー詳細

```
[Browser]                     [Spring Boot]                [Google OAuth]
   │                                │                          │
   │ GET /api/auth/google/login-url │                          │
   ├───────────────────────────────►│                          │
   │ ◄──── { authUrl, state } ──────┤                          │
   │                                │                          │
   │ GET authUrl                    │                          │
   ├──────────────────────────────────────────────────────────►│
   │ ◄────── redirect with code ────────────────────────────────┤
   │                                │                          │
   │ GET /callback?code=&state=     │                          │
   ├───────────────────────────────►│ POST /token exchange     │
   │                                ├─────────────────────────►│
   │                                │ ◄──── id_token ──────────┤
   │                                │ verify, check allowlist  │
   │                                │ issue JWT                │
   │ ◄──── { accessToken, refresh,  │                          │
   │          user } ───────────────┤                          │
```

- state は CSRF 防止用（短命の HMAC 署名トークン or Redis保存。個人利用ならインメモリで可）
- リフレッシュトークンは DB または in-memory 管理、失効可能

---

## OpenAPI

- 設計確定後、`springdoc-openapi` で自動生成（`/v3/api-docs`, `/swagger-ui.html`）
- 開発環境のみ Swagger UI 公開
