-- ============================================================
-- V1: 初期スキーマ
-- 設計: docs/design/01-database-design.md
-- ============================================================

-- 番組名の部分一致検索（pg_trgm）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- areas: エリアマスタ
-- ============================================================
CREATE TABLE areas (
    id          VARCHAR(8)  PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0
);

-- ============================================================
-- stations: 放送局マスタ
-- ============================================================
CREATE TABLE stations (
    id          VARCHAR(32)  PRIMARY KEY,
    area_id     VARCHAR(8)   NOT NULL REFERENCES areas(id),
    name        VARCHAR(100) NOT NULL,
    ascii_name  VARCHAR(100),
    logo_url    TEXT,
    sort_order  INT          NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stations_area ON stations(area_id);

-- ============================================================
-- users: ユーザー
-- ============================================================
CREATE TABLE users (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255),
    picture_url     TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

-- ============================================================
-- user_settings: ユーザー個別設定
-- ============================================================
CREATE TABLE user_settings (
    user_id          BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_area_id  VARCHAR(8)  NOT NULL REFERENCES areas(id),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- user_station_visibility: ユーザー別の放送局表示設定
-- ============================================================
CREATE TABLE user_station_visibility (
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    station_id  VARCHAR(32) NOT NULL REFERENCES stations(id),
    is_visible  BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, station_id)
);

-- ============================================================
-- programs: 番組表
-- ============================================================
CREATE TABLE programs (
    id                    BIGSERIAL    PRIMARY KEY,
    station_id            VARCHAR(32)  NOT NULL REFERENCES stations(id),
    broadcast_start_at    TIMESTAMPTZ  NOT NULL,
    broadcast_end_at      TIMESTAMPTZ  NOT NULL,
    broadcast_date        DATE         NOT NULL,
    day_of_week           SMALLINT     NOT NULL,
    title                 VARCHAR(500) NOT NULL,
    performers            TEXT,
    description           TEXT,
    info                  TEXT,
    image_url             TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_programs_station_start UNIQUE (station_id, broadcast_start_at)
);
CREATE INDEX idx_programs_date_station    ON programs (broadcast_date, station_id);
CREATE INDEX idx_programs_weekly_match    ON programs (station_id, day_of_week, title);
CREATE INDEX idx_programs_title_trgm      ON programs USING GIN (title gin_trgm_ops);

-- ============================================================
-- raw_program_xmls: 生XML保管（仕様変更調査用）
-- ============================================================
CREATE TABLE raw_program_xmls (
    id              BIGSERIAL   PRIMARY KEY,
    station_id      VARCHAR(32) NOT NULL REFERENCES stations(id),
    broadcast_date  DATE        NOT NULL,
    file_path       TEXT        NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_raw_xml UNIQUE (station_id, broadcast_date)
);

-- ============================================================
-- download_registrations: ダウンロード登録
-- ============================================================
CREATE TABLE download_registrations (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    station_id          VARCHAR(32)  NOT NULL REFERENCES stations(id),
    registration_type   VARCHAR(16)  NOT NULL CHECK (registration_type IN ('ONCE', 'WEEKLY')),
    title               VARCHAR(500) NOT NULL,
    broadcast_start_at  TIMESTAMPTZ  NOT NULL,
    broadcast_end_at    TIMESTAMPTZ  NOT NULL,
    day_of_week         SMALLINT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_reg_once
    ON download_registrations (user_id, station_id, broadcast_start_at)
    WHERE registration_type = 'ONCE';
CREATE UNIQUE INDEX uq_reg_weekly
    ON download_registrations (user_id, station_id, title, day_of_week)
    WHERE registration_type = 'WEEKLY';
CREATE INDEX idx_reg_user_status
    ON download_registrations (user_id, status);

-- ============================================================
-- download_histories: ダウンロード履歴
-- ============================================================
CREATE TABLE download_histories (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registration_id     BIGINT       REFERENCES download_registrations(id) ON DELETE SET NULL,
    station_id          VARCHAR(32)  NOT NULL REFERENCES stations(id),
    program_title       VARCHAR(500) NOT NULL,
    performers          TEXT,
    broadcast_start_at  TIMESTAMPTZ  NOT NULL,
    broadcast_end_at    TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(16)  NOT NULL
                            CHECK (status IN ('SUCCESS', 'FAILED', 'EXPIRED')),
    mp3_path            TEXT,
    hls_path            TEXT,
    duration_seconds    INT,
    file_size_bytes     BIGINT,
    error_message       TEXT,
    retry_count         SMALLINT     NOT NULL DEFAULT 0,
    attempted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    notified_at         TIMESTAMPTZ,
    file_deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_hist_match
    ON download_histories (user_id, station_id, broadcast_start_at);
CREATE INDEX idx_hist_library
    ON download_histories (user_id, program_title, broadcast_start_at DESC);
CREATE INDEX idx_hist_badge
    ON download_histories (user_id, status, attempted_at DESC);
CREATE INDEX idx_hist_unnotified
    ON download_histories (user_id, status, notified_at);

-- ============================================================
-- playback_positions: 再生位置
-- ============================================================
CREATE TABLE playback_positions (
    user_id              BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    download_history_id  BIGINT      NOT NULL REFERENCES download_histories(id) ON DELETE CASCADE,
    position_seconds     INT         NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, download_history_id)
);

-- ============================================================
-- batch_executions: バッチ実行履歴
-- ============================================================
CREATE TABLE batch_executions (
    id                  BIGSERIAL   PRIMARY KEY,
    batch_type          VARCHAR(16) NOT NULL CHECK (batch_type IN ('F4', 'F2', 'F4_F2')),
    triggered_by        VARCHAR(16) NOT NULL CHECK (triggered_by IN ('SCHEDULER', 'WEB', 'CLI')),
    triggered_user_id   BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    status              VARCHAR(16) NOT NULL
                            CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_FAILURE', 'FAILED')),
    options             JSONB,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMPTZ,
    summary             TEXT
);
CREATE INDEX idx_batch_running
    ON batch_executions (status, started_at DESC);
