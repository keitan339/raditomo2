// JST 固定で扱うラジコの「放送日」（5:00 区切り）ユーティリティ。
// 文字列ベースで処理し、ローカル TZ に依存しない。

const JST_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

interface JstParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

function jstParts(at?: Date): JstParts {
  const parts = JST_FORMATTER.formatToParts(at ?? new Date());
  const map: Record<string, string> = {};
  for (const p of parts) map[p.type] = p.value;
  // Intl は 24h 設定でも `hour: '24'` を返すケースがあるため 0 に丸める。
  const hour = Number(map.hour) % 24;
  return {
    year: Number(map.year),
    month: Number(map.month),
    day: Number(map.day),
    hour,
    minute: Number(map.minute),
  };
}

/** 引数（または現在時刻）を JST に変換し、5:00 区切りの「放送日」(YYYY-MM-DD) を返す。 */
export function broadcastDate(at?: Date): string {
  const p = jstParts(at);
  // JST の 0:00〜4:59 は前日扱い
  if (p.hour < 5) {
    const d = new Date(Date.UTC(p.year, p.month - 1, p.day));
    d.setUTCDate(d.getUTCDate() - 1);
    return formatYmd(d);
  }
  return `${p.year}-${pad2(p.month)}-${pad2(p.day)}`;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

export function formatYmd(d: Date): string {
  // d は parseYmd で生成した「UTC 0:00 の日付ホルダー」を想定。
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
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
  const month = d.getUTCMonth() + 1;
  const day = d.getUTCDate();
  const dow = DOW_LABELS[d.getUTCDay()];
  return `${month}/${day} (${dow})`;
}

/**
 * ISO 文字列 ("YYYY-MM-DDTHH:mm:ss+09:00") を 5:00 区切りの放送時刻表記
 * (例: 25:00) に整形する。
 */
export function formatBroadcastTime(isoString: string): string {
  // ISO 文字列の先頭から直接抽出（実 JST 時刻に対しタイムゾーン補正を経由しない）。
  const m = isoString.match(/T(\d{2}):(\d{2}):(\d{2})/);
  if (!m) return '';
  const startHour = Number(m[1]);
  const minute = m[2];
  // 5:00 未満の時刻は深夜帯として +24 で表示（例: 01:00 → 25:00）
  const displayHour = startHour < 5 ? startHour + 24 : startHour;
  return `${pad2(displayHour)}:${minute}`;
}

/** ISO の "+09:00" 部分を保ったまま、その時刻が start からの分数を返す。 */
export function diffMinutes(startIso: string, endIso: string): number {
  const start = new Date(startIso).getTime();
  const end = new Date(endIso).getTime();
  return Math.max(0, Math.round((end - start) / 60_000));
}

/**
 * ISO 文字列の JST 時刻から 5:00 区切りの「放送日」(YYYY-MM-DD) を導出する。
 * 例: "2026-04-30T01:00:00+09:00" → "2026-04-29"（25:00 扱い）
 */
export function broadcastDateFromIso(iso: string): string {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2})/);
  if (!m) return '';
  const [, y, mo, d, h] = m;
  if (Number(h) < 5) {
    const date = new Date(Date.UTC(Number(y), Number(mo) - 1, Number(d)));
    date.setUTCDate(date.getUTCDate() - 1);
    return formatYmd(date);
  }
  return `${y}-${mo}-${d}`;
}

/** "M/D (曜) HH:mm 〜 HH:mm" 形式で放送日と時刻を返す。 */
export function formatBroadcastDateRange(startIso: string, endIso: string): string {
  const date = broadcastDateFromIso(startIso);
  return `${formatJpDate(date)} ${formatRange(startIso, endIso)}`;
}

/** 放送開始時刻 ISO から、放送日表示行で使う "HH:mm 〜 HH:mm" を返す。 */
export function formatRange(startIso: string, endIso: string): string {
  return `${formatBroadcastTime(startIso)} 〜 ${formatBroadcastTime(endIso)}`;
}
