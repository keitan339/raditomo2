# テスト戦略

## テスト3層モデル

| 層 | 目的 | ブラウザ操作 | 外部サービス |
|----|------|------------|-------------|
| **UT** (単体テスト) | 単一クラス/メソッドの振る舞いを外部依存なしで検証 | なし | すべてモック |
| **IT** (結合テスト) | 複数コンポーネント・複数層の結合動作を独自サービスで完結して検証。**Playwright によるブラウザE2Eを含む** | あり（Playwright） | 独自サービスで代替（GreenMail / mock-oauth2-server / WireMock / Testcontainers） |
| **ST** (システムテスト) | 本番相当の環境で、実外部サービス（Google / Gmail / ラジコ）との接続を検証 | なし | 実 Google / 実 Gmail / 実ラジコ |

---

## UT（単体テスト）

### Backend（Java / Spring Boot）

| 項目 | 値 |
|------|-----|
| テストランナー | JUnit 5 |
| モック | Mockito |
| アサーション | AssertJ |
| Spring Boot 統合 | `spring-boot-starter-test` |

#### 対象とアプローチ

| 対象 | アプローチ |
|------|----------|
| サービス層 | 全依存を `@Mock` / `@MockBean` で差し替え。ビジネスロジックを純粋に検証 |
| コントローラ層 | `MockMvc` + サービス層モック。HTTP 入出力 / バリデーション / 例外ハンドリングを検証 |
| リポジトリ層 | UT対象外（IT で扱う。H2 等の代替DBは使わない） |
| 外部クライアント（Radiko / Google / Gmail / ffmpeg） | インターフェース定義 → 実装をモック |
| ユーティリティ（時刻計算・サニタイズ等） | 純粋に関数として |

#### 重要な検証ポイント
- **タイムフリー期限切れ判定**: `expiresAt(broadcastStartAt)` のロジック（5:00区切り＋8日）
- **動的照合ロジック**: 一回限り / 毎週マッチング手順1〜4
- **ID3 タグ生成**: 番組名・出演者・Duration（TLEN）のセット
- **JWT 発行・検証**: クレーム・有効期限
- **メール本文生成**: テンプレートレンダリング（実送信はしない）

### Frontend（React / TypeScript）

| 項目 | 値 |
|------|-----|
| テストランナー | Vitest |
| DOM テスト | React Testing Library |
| API モック | MSW (Mock Service Worker) |

#### 対象とアプローチ

| 対象 | アプローチ |
|------|----------|
| コンポーネント単体 | RTL でレンダー。API 呼び出しは MSW でモック |
| カスタム hooks | `renderHook` でテスト |
| ユーティリティ関数（時刻整形等） | 純粋関数として直接テスト |
| Zustand ストア | アクション → 状態変化を検証 |

---

## IT（結合テスト）

### 独自サービスでの代替構成

| 外部サービス | IT での代替 | 配置 |
|-------------|-----------|------|
| PostgreSQL | **Testcontainers** で起動（テストごとにフレッシュ） | テスト中に起動 |
| Gmail SMTP | **GreenMail**（埋込ライブラリ） | 埋込 |
| Google OAuth | **mock-oauth2-server**（Docker） | `docker-compose.test.yml` |
| ラジコ API | **WireMock**（埋込） | 埋込 |
| ffmpeg | 実 ffmpeg（DevContainerに既にあり） | DevContainer |

### Backend IT

| 項目 | 値 |
|------|-----|
| テストフレームワーク | JUnit 5 + Spring Boot Test |
| アノテーション | `@SpringBootTest` (test profile) |
| DB | Testcontainers (`org.testcontainers:postgresql`) |
| HTTP スタブ | WireMock (`com.github.tomakehurst:wiremock-jre8`) |
| SMTP | GreenMail (`com.icegreen:greenmail`) |

#### 重要な検証シナリオ

| シナリオ | 検証内容 |
|---------|---------|
| F4→F2 連鎖 | WireMock でラジコAPI応答 → 番組表DB保存 → F2自動起動 → DL → 履歴記録 |
| ラジコAPI失敗時 | WireMock で 503 を返す → 3回リトライ → FAILED 履歴 |
| メール通知 | 失敗履歴あり → GreenMail に送信 → 件名・本文・宛先検証 |
| OAuth ログイン | mock-oauth2-server で id_token 発行 → 許可リスト判定 → JWT 発行 |
| 排他制御 | 複数の F2 を同時に走らせて1つだけ実行されることを確認 |

### Browser IT (Playwright)

| 項目 | 値 |
|------|-----|
| ブラウザ | Chromium / WebKit (Playwright 標準) |
| 起動方式 | DevContainer 内 |
| 対象 | `docker-compose.test.yml` で起動した IT 環境 |

#### 重要な検証シナリオ

| シナリオ | 検証内容 |
|---------|---------|
| ログインフロー | mock-oauth2-server 経由でログイン → ホーム表示 |
| 番組登録 | 番組表表示 → 番組クリック → 登録モーダル → 登録 → 一覧反映 |
| 録音再生（HLS） | ライブラリ → 番組選択 → hls.js 再生開始 → レジューム位置保存 |
| バッチ手動実行 | ボタン押下 → 進捗ポーリング → 完了表示 |
| 履歴削除（期限切れ警告） | 期限切れ番組の削除時に「再ダウンロードできません」警告表示 |

### Frontend IT
- バックエンドが起動した状態で Vitest + MSW（バックエンドが返す形式）で連携シナリオを検証
- もしくは Playwright の Browser IT で代替

---

## ST（システムテスト）

### 半自動スクリプト方式

リリース前または週次で実行する、実外部サービス接続のスモークテスト。

#### 対象シナリオ

| 種類 | 方式 | スクリプト |
|------|------|---------|
| Gmail SMTP 送信 | 自動 | `scripts/st/gmail-smoke.sh` — テストメール1通送信→受信を IMAP/Gmail API で検証 |
| ラジコ auth1/auth2 | 自動 | `scripts/st/radiko-auth-check.sh` — 認証成功＋area_id 取得を確認 |
| ラジコ番組表取得 | 自動 | `scripts/st/radiko-program-fetch.sh` — 1放送局・1日分のXML取得を確認 |
| ラジコ短時間DL | 自動 | `scripts/st/radiko-download-sample.sh` — 短時間（5分）番組のDL→MP3化 |
| Google OAuth ログイン | 手動 | チェックリスト（ブラウザでログイン画面 → Google → 戻り先で `/api/auth/me` 200） |
| End-to-End シナリオ | 手動 | チェックリスト（登録→DL→再生→メール通知） |

#### スクリプト実行環境
- DevContainer 内で実行可能（curl, jq, ffmpeg 等は揃っている）
- 環境変数 `.env.st` から認証情報を読み込み
- 実 Gmail のテスト用アプリパスワードを使用（リポジトリには `.env.st.example` のみコミット）

#### 実行頻度
- リリース前: 全スクリプト実行
- 週次（任意）: ラジコ系スクリプトのみ

---

## テスト用 Docker 構成

`docker-compose.test.yml`（IT 用）：

```yaml
services:
  test-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: radiko_test
      POSTGRES_USER: radiko
      POSTGRES_PASSWORD: test
      TZ: Asia/Tokyo
    ports:
      - "15432:5432"

  mock-oauth:
    image: ghcr.io/navikt/mock-oauth2-server:2.x
    ports:
      - "18080:8080"
    environment:
      JSON_CONFIG: |
        {
          "interactiveLogin": false,
          "tokenCallbacks": [...]
        }
```

※ 通常の Backend IT は Testcontainers が DB を自動起動するため `test-db` は不要。Playwright IT のときに `docker-compose.test.yml` 全体を起動する。

---

## カバレッジ

### 目標

| 種類 | 目標 |
|------|-----|
| Backend (Java) | **行カバレッジ 80% 以上**（プロジェクト全体集計） |
| Frontend (React) | **行カバレッジ 80% 以上** |
| 計測対象 | UT のみ（IT/ST はカバレッジ計測対象外） |

### ツール

| プロジェクト | ツール |
|-------------|--------|
| Backend | **JaCoCo**（`maven-jacoco-plugin`） |
| Frontend | **Vitest coverage**（v8 provider） |

### CI 失敗条件

- カバレッジが 80% を下回ったらビルド失敗
- 設定例（JaCoCo）:
  ```xml
  <execution>
    <id>jacoco-check</id>
    <goals><goal>check</goal></goals>
    <configuration>
      <rules>
        <rule>
          <element>BUNDLE</element>
          <limits>
            <limit>
              <counter>LINE</counter>
              <value>COVEREDRATIO</value>
              <minimum>0.80</minimum>
            </limit>
          </limits>
        </rule>
      </rules>
    </configuration>
  </execution>
  ```

### 計測除外
- DTO / Entity（getter/setter のみのクラス）
- 設定クラス（`@Configuration` のみ）
- main クラス
- 自動生成コード

---

## CI（GitHub Actions）

### ワークフロー構成

`.github/workflows/ci.yml`:

```yaml
name: CI

on: [push, pull_request]

jobs:
  backend-ut:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
      - run: mvn -B test          # UT のみ実行
      - run: mvn -B jacoco:report jacoco:check  # カバレッジ80%チェック
      - uses: actions/upload-artifact@v4
        with:
          name: backend-coverage
          path: target/site/jacoco/

  backend-it:
    runs-on: ubuntu-latest
    services:
      mock-oauth:
        image: ghcr.io/navikt/mock-oauth2-server:2.x
        ports: [18080:8080]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
      - run: mvn -B verify -Pit   # IT 実行（Testcontainers が DB を起動）

  frontend-ut:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - working-directory: frontend
        run: |
          npm ci
          npm run test -- --coverage
          npm run typecheck
          npm run lint

  browser-it:
    runs-on: ubuntu-latest
    needs: [backend-it, frontend-ut]
    steps:
      - uses: actions/checkout@v4
      - run: docker compose -f docker-compose.test.yml up -d
      - run: npx playwright install --with-deps
      - run: npm run test:e2e
```

### ST は CI 対象外
- ST は手動（リリース前にローカル or 専用サーバで実行）
- 必要に応じて GitHub Actions の `workflow_dispatch` で手動実行できるようにする

---

## ディレクトリ構成

### Backend
```
backend/
├── src/main/java/com/raditomo/...
├── src/test/java/com/raditomo/...        ← UT
└── src/it/java/com/raditomo/...          ← IT（Maven Failsafe）
```

Maven プラグイン: `maven-surefire-plugin`（UT）+ `maven-failsafe-plugin`（IT）。`mvn test` で UT、`mvn verify` で UT+IT。

### Frontend
```
frontend/
├── src/...
├── src/__tests__/...                     ← UT (Vitest)
└── e2e/                                  ← Browser IT (Playwright)
```

### ST スクリプト
```
scripts/st/
├── gmail-smoke.sh
├── radiko-auth-check.sh
├── radiko-program-fetch.sh
├── radiko-download-sample.sh
└── README.md                             ← 手動チェックリスト含む
```
