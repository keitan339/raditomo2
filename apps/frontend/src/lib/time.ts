// JST 固定で扱うラジコの「放送日」（5:00 区切り）ユーティリティ。
// 文字列ベースで処理し、ローカル TZ に依存しない。

const JST_OFFSET_MIN = 9 * 60;

function nowJst(): Date {
  const now = new Date();
  return new Date(now.getTime() + (JST_OFFSET_MIN + now.getTimezoneOffset()) * 60_000);
}

/** 引数（または現在時刻）を JST に変換し、5:00 区切りの「放送日」(YYYY-MM-DD) を返す。 */
export function broadcastDate(at?: Date): string {
  const jst = at ? new Date(at.getTime() + (JST_OFFSET_MIN + at.getTimezoneOffset()) * 60_000) : nowJst();
  // 5:00 未満は前日扱い
  if (jst.getUTCHours() < 5) {
    jst.setUTCDate(jst.getUTCDate() - 1);
  }
  return formatYmd(jst);
}

export function formatYmd(jst: Date): string {
  const y = jst.getUTCFullYear();
  const m = String(jst.getUTCMonth() + 1).padStart(2, '0');
  const d = String(jst.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** "YYYY-MM-DD" → "YYYYMMDD"。バックエンドの date クエリ用。 */
export function compactYmd(ymd: string): string {
  return ymd.replaceAll('-', '');
}

/** "YYYY-MM-DD" を JST 0:00 を表す Date オブジェクトに。日付演算用。 */
export function parseYmd(ymd: string): Date {
  const [y, m, d] = ymd.split('-').map((n) => Number(n));
  return new Date(Date.UTC(y, m - 1, d));
}

export function addDays(ymd: string, days: number): string {
  const d = parseYmd(ymd);
  d.setUTCDate(d.getUTCDate() + days);
  return formatYmd(d);
}

const DOW_LABELS = ['日', '月', '火', '水', '木', '金', '土'];

export function formatJpDate(ymd: string): string {
  const d = parseYmd(ymd);
  const m = d.getUTCMonth() + 1;
  const day = d.getUTCDate();
  const dow = DOW_LABELS[d.getUTCDay()];
  return `${m}/${day} (${dow})`;
}

/**
 * ISO 文字列 ("YYYY-MM-DDTHH:mm:ss+09:00") を 5:00 区切りの放送時刻表記
 * (例: 25:00) に整形する。
 */
export function formatBroadcastTime(isoString: string): string {
  // タイムゾーン補正を避けるため、 ISO 文字列の先頭から直接抽出する。
  const m = isoString.match(/T(\d{2}):(\d{2}):(\d{2})/);
  if (!m) return '';
  const startHour = Number(m[1]);
  const minute = m[2];
  // この時刻が「broadcastDate ベース」だとしたら、5:00 未満は前日扱いになる
  // → 表示上は +24 して 25:00 のように見せる必要がある。
  // bdate = 番組DTOの broadcastDate を持っていない → 5:00 未満の hh は +24 と仮定する
  // （ラジコの番組表データではこの仮定が成り立つ）
  const displayHour = startHour < 5 ? startHour + 24 : startHour;
  return `${String(displayHour).padStart(2, '0')}:${minute}`;
}

/** ISO の "+09:00" 部分を保ったまま、その時刻が start からの分数を返す。 */
export function diffMinutes(startIso: string, endIso: string): number {
  const start = new Date(startIso).getTime();
  const end = new Date(endIso).getTime();
  return Math.max(0, Math.round((end - start) / 60_000));
}

/** 放送開始時刻 ISO から、放送日表示行で使う "HH:mm 〜 HH:mm" を返す。 */
export function formatRange(startIso: string, endIso: string): string {
  return `${formatBroadcastTime(startIso)} 〜 ${formatBroadcastTime(endIso)}`;
}
