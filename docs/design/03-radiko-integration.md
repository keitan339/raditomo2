# ラジコAPI連携設計

参考実装: [rec_radiko_ts](https://github.com/uru2/rec_radiko_ts)（2026年1月仕様変更対応済み）

## エリア制限の前提

ラジコのフリー会員は **接続元IPの居住エリア（auth2 が返す `area_id`）の放送局のみ聴取可能**（要件「フリー会員を対象、エリアフリー非対応」）。

このアプリで設定可能なエリアは、**サーバー設置場所からラジコへ接続したときに `auth2` が返す `area_id`** に限定される。設定画面で別エリアを選択しても、認証時に拒否されて番組表取得・タイムフリーDLが失敗する。

### 実装上の対応

- アプリ起動時 / エリア変更時に `auth2` を呼び出し、返される `area_id` をログに記録
- ユーザーが設定したエリアと `auth2` の `area_id` が異なる場合は警告ログを出力
- F4・F2 失敗時のメール通知本文に「エリア不一致の可能性」を含めて、運用者がトラブルシュートできるようにする

## ラジコ認証 (auth1 / auth2)

ラジコはアカウント不要でデバイスレベル認証。フリー会員相当として動作する。

### auth1
```
GET https://radiko.jp/v2/api/auth1
Headers:
  X-Radiko-App: pc_html5
  X-Radiko-App-Version: 0.0.1
  X-Radiko-User: dummy_user
  X-Radiko-Device: pc

Response Headers:
  X-Radiko-AuthToken: <token>
  X-Radiko-KeyOffset: <int>
  X-Radiko-KeyLength: <int>
```

### auth2
- 共通鍵（`bcd151073c03b352e1ef2fd66c32209da9ca0afa` 等のクライアント側既知の鍵）から `KeyOffset` / `KeyLength` の範囲を切り出し、Base64 エンコードして `X-Radiko-Partialkey` として送信
- レスポンスボディに `area_id, area_name, ...` が返る → エリア確認

```
GET https://radiko.jp/v2/api/auth2
Headers:
  X-Radiko-AuthToken: <token>
  X-Radiko-Partialkey: <base64>
  X-Radiko-User: dummy_user
  X-Radiko-Device: pc
```

### 実装方針
- 認証トークンはアプリ起動時または有効期限切れ時に取得
- 有効期限は数時間（明示的な仕様なし）→ 3時間ごとに再取得 or 失敗時に再取得
- 共通鍵はビルド時定数（`application.yml` または定数クラス）。OSS参考実装に合わせる

---

## 番組表取得 (F4)

### 取得API（候補）

ラジコの番組表APIは複数ある。本システムでは以下を使用：

| URL | 説明 |
|-----|------|
| `https://radiko.jp/v3/program/date/{YYYYMMDD}/{areaId}.xml` | 指定エリア・指定日の全放送局番組表 |
| `https://radiko.jp/v3/program/station/date/{YYYYMMDD}/{stationId}.xml` | 指定放送局・指定日の番組表 |

→ **エリア単位で取得（前者）** をデフォルトとする。1リクエストでエリア全局を取得可能。

### 取得範囲

- 過去1週間（タイムフリー対象期間）
- 今日を含む未来1週間程度

→ 計 14日分 × エリア数。1日1リクエスト想定なので 1ユーザー設定エリアで 1日あたり 14リクエスト。

### XML パース

- 実装: `javax.xml.parsers.DocumentBuilder` または `Jackson XML` (`jackson-dataformat-xml`)
- パース対象: `<radiko><stations><station id="TBS"><progs><prog ft="20260424050000" to="20260424080000" dur="10800"><title>...</title><pfm>...</pfm></prog>...`
- 放送局単位（`<station>`）で try/catch し、失敗したらその局のみスキップ → DBへの上書き更新は他局成功分のみ

### 生XML保存

- パスフォーマット: `{XML_BASE_PATH}/{YYYYMMDD}/{areaId}.xml`
  - 例: `/data/xml/20260424/JP13.xml`
- 上書き保存。`raw_program_xmls` テーブルにも upsert

### F4 完了 → F2 自動起動

- F4 のメインジョブメソッド完了後、Spring の `ApplicationEventPublisher` で `ProgramFetchCompletedEvent` を発行
- F2 のリスナーが `@TransactionalEventListener(phase = AFTER_COMMIT)` で受信して F2 を非同期実行
- 部分失敗（一部局失敗）でも DBコミットされていれば F2 は走る

---

## タイムフリーダウンロード (F2)

2026年1月仕様変更後、`smartstream.ne.jp` 経由のチャンク分割取得方式となった。

### プレイリスト取得

```
GET https://radiko.jp/v2/api/ts/playlist.m3u8?station_id={STATION_ID}&l=15&ft={YYYYMMDDHHMMSS}&to={YYYYMMDDHHMMSS}
Headers:
  X-Radiko-AuthToken: <token>
```

- レスポンスは m3u8（マスタープレイリスト）
- マスタープレイリスト内の URL を辿ると、`smartstream.ne.jp` を含むチャンクリスト m3u8 が得られる
- チャンクリストには複数の `.aac` セグメント URL が並ぶ（数十〜数百個）

### 番組単位の処理は直列、チャンクのみ並列

**重要**: F2 は **1番組ずつ直列に処理** する（要件 F2 通り）。並列処理を行うのは「1つの番組内のチャンクダウンロード」のみ。

```
F2 実行
├── 番組A をダウンロード
│   └── チャンク1 / 2 / 3 / ... を並列DL（並列度8） → 連結 → MP3 → HLS変換
├── 番組B をダウンロード
│   └── チャンク1 / 2 / 3 / ... を並列DL → 連結 → MP3 → HLS変換
└── 番組C を ...
```

理由:
- 複数番組を並列にすると、ラジコ側のレート制限に引っ掛かりやすい
- 1番組失敗時のリトライ・部分ファイル削除・履歴記録のロジックがシンプルになる

### チャンク並列取得（1番組内）

1. マスタープレイリスト → メディアプレイリスト（チャンクリスト）取得
2. チャンクリストから全 `.aac` セグメントURLを抽出
3. **並列度 N**（デフォルト 8）で並列ダウンロード（`HttpClient` + `CompletableFuture` または Java 21 の Virtual Threads）
4. 全セグメント取得完了後、順序通りに連結

### 並列度の制御

- 環境変数 `RADIKO_DOWNLOAD_CONCURRENCY=8` で設定可能
- レート制限を避けるため、初期値は控えめ（8並列程度）
- 要技術検証: ラジコ側のレート制限の実態
- 並列度を上げすぎると 403/429 が返る可能性 → リトライロジックで吸収

### MP3 化

- 取得した AAC セグメントを ffmpeg で連結 + MP3 変換
- コマンド例:
  ```bash
  ffmpeg -i "concat:seg1.aac|seg2.aac|...|segN.aac" \
         -c:a libmp3lame -b:a 128k \
         -id3v2_version 3 \
         -metadata title="..." \
         -metadata artist="..." \
         -metadata album="..." \
         -metadata album_artist="..." \
         -metadata date="20260224" \
         -metadata year="2026" \
         output.mp3
  ```
- 実装: Java から `ProcessBuilder` で ffmpeg を起動。標準エラー出力をログに記録

### ID3タグ付与

- ffmpeg の `-metadata`（ID3v2.3）で全タグを付与する
- Album Artist は `-metadata album_artist=...`、Date/Year も `-metadata` で対応
- Duration（要件 F1）は MP3 フレームヘッダで再現するため明示書き込みは行わない（詳細は `05-batch-file-notification.md` 参照）
- mp3agic は依存に残しているが、現時点での出力経路はすべて ffmpeg 経由

### HLS 事前変換

- 同じ MP3 を ffmpeg で HLS に再変換（再生時のレスポンス向上のため）
- コマンド例:
  ```bash
  ffmpeg -i input.mp3 \
         -c:a aac -b:a 128k \
         -hls_time 10 \
         -hls_list_size 0 \
         -hls_segment_filename "segment_%03d.ts" \
         playlist.m3u8
  ```
- HLS 変換失敗時は MP3 も削除し「ダウンロード失敗」として履歴記録

### リトライ

- ダウンロード失敗時はその場で最大3回リトライ
- リトライ前に部分ファイル（途中までの AAC、MP3、HLS）を削除
- 3回失敗 → スキップして次番組

### タイムフリー期限切れ判定

- 放送日（5:00区切り）+ 7日 → 8日目の 05:00:00 JST に期限切れ
- F2 実行時点で `now() > 期限切れ時刻` の番組は `EXPIRED` で履歴記録
- 一回限り登録なら `download_registrations.status` も `EXPIRED` に更新

---

## ラジコAPIクライアント実装方針

### 構成

```
radiko/
├── RadikoAuthService.java         # auth1/auth2 認証
├── RadikoProgramFetcher.java      # 番組表XML取得・パース
├── RadikoTimefreeDownloader.java  # チャンク並列DL
├── Mp3Encoder.java                # ffmpeg連結→MP3
├── HlsConverter.java              # MP3→HLS変換
└── dto/                           # XMLマッピング用DTO
```

### HTTP クライアント
- Java 標準 `java.net.http.HttpClient` を使用（Virtual Threads と相性良）
- リトライ・タイムアウト: 接続 10s / 読み込み 30s

### 並列処理
- Java 21 の Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) で並列DL
- セマフォで並列度を制御

### ログ出力
- ダウンロード進捗（%）はログレベル DEBUG
- 失敗詳細は ERROR
- バッチ完了時にサマリ INFO

---

## 技術検証項目（実装前にプロトタイプで確認）

| 項目 | 確認内容 |
|------|---------|
| auth1/auth2 共通鍵 | OSS実装の鍵が現時点で有効か |
| エリア外 station へのアクセス | エリア制限がどう返るか |
| チャンク並列度の上限 | 何並列まで安全か（429・403が出ない閾値） |
| ffmpeg AAC→MP3 連結 | 音質劣化・ノイズの発生有無 |
| HLS 変換時間 | 1時間番組で何秒かかるか |
| 番組表XMLのスキーマ | 2026年版での要素・属性確認 |
