package com.raditomo.common.time;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * JST 時刻ユーティリティ。
 *
 * - 5:00 区切りの「放送日」: 5:00 未満なら前日扱い
 * - タイムフリー期限: 放送日 + 8 日 5:00 JST
 * - radiko 形式の日時パース（yyyyMMddHHmmss、JST 無付加）
 */
public final class JstTimes {
    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    public static final ZoneOffset JST_OFFSET = ZoneOffset.ofHours(9);
    public static final LocalTime DAY_BOUNDARY = LocalTime.of(5, 0);

    private static final DateTimeFormatter RADIKO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private JstTimes() {}

    /** 5:00 区切りの放送日（JST）。 */
    public static LocalDate broadcastDate(OffsetDateTime broadcastStart) {
        ZonedDateTime jst = broadcastStart.atZoneSameInstant(JST);
        if (jst.toLocalTime().isBefore(DAY_BOUNDARY)) {
            return jst.toLocalDate().minusDays(1);
        }
        return jst.toLocalDate();
    }

    /** 放送日基準の曜日。0=日, 1=月, ..., 6=土。 */
    public static short dayOfWeek(LocalDate broadcastDate) {
        // DayOfWeek: MONDAY=1 ... SUNDAY=7。要件は 0=日 ... 6=土。
        DayOfWeek dow = broadcastDate.getDayOfWeek();
        return (short) (dow == DayOfWeek.SUNDAY ? 0 : dow.getValue());
    }

    /** タイムフリー期限切れ時刻（放送日 + 8 日 5:00 JST）。 */
    public static OffsetDateTime expiresAt(OffsetDateTime broadcastStart) {
        LocalDate bd = broadcastDate(broadcastStart);
        return bd.plusDays(8).atTime(DAY_BOUNDARY).atOffset(JST_OFFSET);
    }

    /** "20260424050000" → OffsetDateTime（JST）。 */
    public static OffsetDateTime parseRadikoDateTime(String s) {
        LocalDateTime ldt = LocalDateTime.parse(s, RADIKO_FORMAT);
        return ldt.atOffset(JST_OFFSET);
    }
}
