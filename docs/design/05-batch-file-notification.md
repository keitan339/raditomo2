# バッチ・ファイル保存・通知 詳細設計

## バッチ処理アーキテクチャ

### 全体構成

```
[Spring Scheduler] ──────┐
                          ▼
                   ┌──────────────┐
[CLI mode]   ─────►│  BatchRunner │
                   └──────┬───────┘
                          │
[Web API ────────►        ▼
 /api/batch/run]   ┌──────────────────────────────────────┐
                   │ F4Job (ProgramTableFetchJob)         │
                   └──────┬───────────────────────────────┘
                          │ ProgramFetchCompletedEvent
                          ▼
                   ┌──────────────────────────────────────┐
                   │ F2Job (TimefreeDownloadJob)          │
                   └──────────────────────────────────────┘
```

### スケジューラー設定

毎朝 **5:30 JST** に F4→F2 を自動実行する（要件 F4 通り）。

```java
@Component
public class DailyScheduler {

    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Tokyo")
    public void runDailyBatch() {
        // F4 を実行 → 完了イベント発火 → F2 が連鎖実行（後述「F4→F2 連鎖判定」参照）
        programTableFetchJob.run(TriggeredBy.SCHEDULER);
    }
}
```

`application.yml`:
```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2
```

### 起動モード

Spring Boot アプリは1つの Jar から複数モードで起動可能：

| モード | 起動方法 | 説明 |
|--------|---------|------|
| Web | `java -jar app.jar` | 通常の Web サーバー＋ Spring Scheduler |
| CLI | `java -jar app.jar --cli download --date 20260424 --force` | バッチ実行のみ。完了後 exit |
| Init | `java -jar app.jar --cli users add user@example.com` | 許可リスト管理など |

実装: **Picocli** を使用。`--cli` 引数があるかで `SpringApplication.exit(...)` 分岐。

### CLI ライブラリ選定

- **Picocli** を採用（`info.picocli:picocli:4.x`）
- 理由: アノテーションベース、Spring Boot 統合あり、ヘルプ自動生成

### サブコマンド構成

| サブコマンド | 機能 | オプション |
|-------------|------|------------|
| `download-programs` | 番組表取得（F4 単体） | なし（全範囲取得） |
| `download-audio` | タイムフリーDL（F2 単体） | `--date YYYYMMDD`, `--force` |
| `download` | 番組表取得＋音声DL（F4→F2 一括） | `--date YYYYMMDD`, `--force` |
| `users add <email>` | 許可リストにユーザー追加 | |
| `users remove <email>` | 許可リストからユーザー削除（論理削除） | |
| `users list` | 許可リスト一覧表示 | |

### ホストラッパースクリプト

リポジトリ直下に `raditomo` シェルスクリプトを配置し、`docker compose exec` をラップする：

```bash
#!/bin/bash
# raditomo - CLI wrapper
exec docker compose exec app java -jar /app/app.jar --cli "$@"
```

利用例：
```bash
./raditomo download-programs
./raditomo download-audio --date 20260424
./raditomo download --force
./raditomo users add user@example.com
./raditomo users list
```

---

## F4 → F2 連鎖判定

### 連鎖マトリクス

`triggeredBy` の値で F4 完了後に F2 を実行するかを決定する：

| `triggeredBy` | 起動契機 | F2 連鎖 |
|---------------|---------|---------|
| `SCHEDULER` | 毎朝5:30の自動実行 | **あり** |
| `WEB_F4_F2` | Web画面「F4→F2 一括実行」 | **あり** |
| `CLI_DOWNLOAD` | `./raditomo download` | **あり** |
| `WEB_F4` | Web画面「F4 単体実行」 | なし |
| `CLI_DOWNLOAD_PROGRAMS` | `./raditomo download-programs` | なし |
| `WEB_AREA_CHANGE` | エリア変更時の自動 F4 | なし |

### 実装方式: Spring イベント

```java
// F4 ジョブ完了時
applicationEventPublisher.publishEvent(new ProgramFetchCompletedEvent(triggeredBy));

// F2 リスナー
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void onProgramFetchCompleted(ProgramFetchCompletedEvent event) {
    if (event.getTriggeredBy().shouldChainF2()) {
        timefreeDownloadJob.run(TriggeredBy.fromChain(event.getTriggeredBy()));
    }
}
```

- `AFTER_COMMIT` で F4 のDB変更が確定してから F2 が走る
- 部分失敗（一部局のパース失敗）でもコミットは行うため F2 は走る（要件 F4「部分失敗でもF2は実行」）

### バッチの排他制御

- 同時実行されては困るバッチ:
  - F2: 1ユーザー＝同時1つまで
  - F4: 同一エリア対象＝同時1つまで
- 実装: DBトランザクションで `batch_executions` を行ロック。`SELECT ... FOR UPDATE` で `RUNNING` レコードがあれば拒否

---

## 非同期実行（Web API から）

### 実装

```java
@PostMapping("/api/batch/run")
public BatchRunResponse run(@RequestBody BatchRunRequest req, @AuthenticationPrincipal User user) {
    // 排他制御チェック → batch_executions に RUNNING で挿入
    BatchExecution exec = batchExecutionService.startNewExecution(req, user);
    asyncBatchRunner.runAsync(exec.getId(), req); // @Async メソッド
    return new BatchRunResponse(exec.getId(), "RUNNING");
}

@Async("batchTaskExecutor")
public void runAsync(Long execId, BatchRunRequest req) {
    try {
        switch (req.getType()) {
            case F4 -> programTableFetchJob.run(...);
            case F2 -> timefreeDownloadJob.run(...);
            case F4_F2 -> { programTableFetchJob.run(...); /* F2 はイベント経由 */ }
        }
        batchExecutionService.markSuccess(execId);
    } catch (Exception e) {
        batchExecutionService.markFailed(execId, e);
    }
}
```

### 状態確認
- フロントは `GET /api/batch/executions/{id}` を 3〜5秒間隔でポーリング
- レスポンスに `progress`（任意）を返す（DL中の番組数 / 総数 など）

---

## ファイル保存設計

### ベースパス（環境変数）

| 環境変数 | デフォルト | 用途 |
|---------|-----------|------|
| `RECORDINGS_BASE_PATH` | `/data/recordings` | MP3 + HLS のルート |
| `XML_BASE_PATH` | `/data/xml` | 番組表生XMLのルート |
| `LOG_BASE_PATH` | `/var/log/app` | アプリログ |

### 録音ファイル構成（要件 F2 通り）

要件の `{account_id}` = DB の `users.id`（ユーザーID）として扱う。本ドキュメントでは以降 **`{user_id}`** で統一表記する。

```
/data/recordings/
└── {user_id}/                        # = users.id（要件の {account_id}）
    ├── mp3/
    │   └── {番組名}/
    │       └── {番組名}_{YYYYMMDD-HHMM}.mp3
    └── hls/
        └── {番組名}/
            └── {YYYYMMDD-HHMM}/
                ├── playlist.m3u8
                ├── segment_000.ts
                ├── segment_001.ts
                └── ...
```

### XML 保存

```
/data/xml/
└── {YYYYMMDD}/
    └── {areaId}.xml
```

### ファイル名サニタイズ

- 番組名に含まれる以下の文字をサニタイズ（置換: `_`）
  - `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, 改行、制御文字
- サニタイズ後の番組名を `programs.title` とは別に履歴やパス生成に使用するか、または毎回サニタイズ関数を通す
  - → 設計: パス生成時に都度サニタイズ。DBにはオリジナルの `title` を保持

### ID3タグ実装

- ライブラリ: **mp3agic** (`com.mpatric:mp3agic:0.9.x`)
- 理由: 軽量、シンプル、Java 標準の代替として実績あり
- ffmpeg の `-metadata` 経由でも可能だが、Album Artist 等を含めた厳密な制御は mp3agic で行う

```java
Mp3File mp3 = new Mp3File(filePath);
ID3v2 tag = new ID3v24Tag();
tag.setTitle(programTitle + "_" + dateTime);                   // 例: オールナイトニッポン_20260222-0100
tag.setArtist(performers);                                      // 出演者
tag.setAlbum(programTitle);                                     // 番組名
tag.setAlbumArtist(stationNameJa);                              // 放送局の和名
tag.setYear(String.valueOf(broadcastStartAt.getYear()));        // 放送開始年（YYYY）
tag.setDate(yyyymmddHHMM);                                      // 放送開始日時（YYYYMMDD-HHMM）

// Duration: 放送時間（ID3v2 の TLEN フレーム、ミリ秒単位）
long durationMillis = Duration.between(broadcastStartAt, broadcastEndAt).toMillis();
tag.setLength(String.valueOf(durationMillis));                  // mp3agic: ID3v24Tag.setLength() → TLEN フレーム

mp3.setId3v2Tag(tag);
mp3.save(filePath + ".tmp");
Files.move(Paths.get(filePath + ".tmp"), Paths.get(filePath), REPLACE_EXISTING);
```

※ MP3ファイル自体の再生時間は ffmpeg 出力時に自動的にメタデータとしても付与されるが、要件「Duration: 放送時間」を満たすため `TLEN` フレームに明示的に放送時間（ミリ秒）をセットする。

### ファイル削除（F3 から）

- `download_histories.mp3_path`, `hls_path` から物理削除
- HLS は `hls_path` が指す playlist.m3u8 のディレクトリごと削除（segment ファイル含む）
- DBレコード（履歴）は `file_deleted_at` 設定で残す

---

## 通知設計

### Gmail SMTP

- 認証方式: **Gmail アプリパスワード**
  - 個人利用前提、シンプル、セットアップ容易
  - OAuth 2.0 はリフレッシュトークン管理が複雑
- ライブラリ: `spring-boot-starter-mail` + Jakarta Mail
- 設定（環境変数）:
  ```
  SMTP_HOST=smtp.gmail.com
  SMTP_PORT=587
  SMTP_USERNAME=sender@gmail.com
  SMTP_PASSWORD=<app-password>  # アプリパスワード
  NOTIFICATION_FROM=sender@gmail.com
  ```

### 送信タイミング

- F2 / F4 バッチ完了時、その実行で発生した「失敗」「期限切れ」「番組表取得エラー」をまとめて1通送信
- **自動実行（毎朝5:30）／ Web画面からの手動実行 ／ CLI からの手動実行 のいずれでも同じ通知ロジックが走る**（要件「バッチ実行ごとに、その日の失敗・期限切れをまとめてメールで通知」）
- 通知先: 各ユーザーの Google アカウントメール（`users.email`）
- 「同じ番組が連日失敗」も毎日通知する仕様 → `download_histories.notified_at` で「今回の実行で記録された失敗のうち未通知」を抽出 → 送信後 `notified_at = NOW()` で更新
- 失敗・期限切れが0件の場合は通知メールを送らない（無駄な通知抑制）

### メールテンプレート

#### F2 失敗通知
```
件名: [Raditomo] ダウンロード失敗通知 (2026/04/24)

本文:
2026年4月24日のダウンロード処理で以下の問題が発生しました。

【ダウンロード失敗】3件
  - TBSラジオ オールナイトニッポン (2026/04/24 25:00)
    エラー: HTTP 403 (3回リトライ後失敗)
  - ...

【タイムフリー期限切れ】1件
  - 文化放送 ○○ラジオ (2026/04/17 15:00)

詳細はサーバーログをご確認ください。
```

#### F4 エラー通知
```
件名: [Raditomo] 番組表取得エラー通知 (2026/04/24)

本文:
2026年4月24日の番組表取得で以下のエラーが発生しました。

【取得失敗】
  - 4/22 文化放送
  - 4/23 J-WAVE

詳細はサーバーログをご確認ください。
```

### バッジ表示

- バックエンド: `GET /api/histories/badge` で `failedCount + expiredCount` を返す
- フロント: 30秒間隔でポーリング（TanStack Query の `refetchInterval`）
- WebSocket は採用しない（更新頻度低・個人利用のためオーバースペック）

### 通知失敗時の扱い

- SMTP 送信失敗はログに ERROR 出力
- `notified_at` は更新しない → 次回バッチで再送リトライ
- 連続失敗時のリトライ抑制は将来課題（個人利用では SMTP 不調は稀）

---

## ロギング

- Spring Boot 標準（Logback）
- 出力先: ファイル + コンソール
- ローテート: 日次 + サイズ上限 100MB、保持 30日

### ログファイル分離

| ファイル | 内容 |
|---------|------|
| `/var/log/app/app.log` | API・通常ログ（Spring本体・Webリクエストなど） |
| `/var/log/app/batch.log` | F2/F4 バッチ実行ログ（番組表取得・タイムフリーDL・通知送信） |

実装方針: Logback の `<logger name="...">` でパッケージ単位で振り分ける。

```xml
<!-- logback-spring.xml（概略） -->
<configuration>
  <appender name="APP_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_BASE_PATH}/app.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>${LOG_BASE_PATH}/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <appender name="BATCH_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_BASE_PATH}/batch.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>${LOG_BASE_PATH}/batch.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- バッチパッケージは batch.log のみへ -->
  <logger name="com.raditomo.batch" level="INFO" additivity="false">
    <appender-ref ref="BATCH_FILE"/>
    <appender-ref ref="CONSOLE"/>
  </logger>

  <!-- ラジコAPI連携も batch.log へ -->
  <logger name="com.raditomo.radiko" level="INFO" additivity="false">
    <appender-ref ref="BATCH_FILE"/>
    <appender-ref ref="CONSOLE"/>
  </logger>

  <root level="INFO">
    <appender-ref ref="APP_FILE"/>
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

※ パッケージ名（`com.raditomo.*`）は実装フェーズで確定。
