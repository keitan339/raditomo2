package com.raditomo.common.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JstTimesTest {

    @Test
    void broadcastDate_returnsSameDateAfterBoundary() {
        OffsetDateTime t = OffsetDateTime.parse("2026-04-24T05:00:00+09:00");
        assertThat(JstTimes.broadcastDate(t)).isEqualTo(LocalDate.of(2026, 4, 24));
    }

    @Test
    void broadcastDate_returnsPreviousDateBeforeBoundary() {
        OffsetDateTime t = OffsetDateTime.parse("2026-04-25T04:59:00+09:00");
        assertThat(JstTimes.broadcastDate(t)).isEqualTo(LocalDate.of(2026, 4, 24));
    }

    @Test
    void expiresAt_returnsBroadcastDatePlus8DaysAt5() {
        // 4/24 5:00〜 → 期限切れ = 5/2 5:00
        OffsetDateTime t = OffsetDateTime.parse("2026-04-24T05:00:00+09:00");
        assertThat(JstTimes.expiresAt(t))
                .isEqualTo(OffsetDateTime.parse("2026-05-02T05:00:00+09:00"));
    }

    @Test
    void expiresAt_handlesPreBoundaryStart() {
        // 4/25 4:59 開始 → 放送日は 4/24 → 期限 = 5/2 5:00
        OffsetDateTime t = OffsetDateTime.parse("2026-04-25T04:59:00+09:00");
        assertThat(JstTimes.expiresAt(t))
                .isEqualTo(OffsetDateTime.parse("2026-05-02T05:00:00+09:00"));
    }

    @Test
    void dayOfWeek_returnsZeroForSundayThroughSixForSaturday() {
        assertThat(JstTimes.dayOfWeek(LocalDate.of(2026, 4, 26))).isEqualTo((short) 0); // 日
        assertThat(JstTimes.dayOfWeek(LocalDate.of(2026, 4, 27))).isEqualTo((short) 1); // 月
        assertThat(JstTimes.dayOfWeek(LocalDate.of(2026, 5, 2))).isEqualTo((short) 6); // 土
    }

    @Test
    void parseRadikoDateTime_yieldsJstOffset() {
        OffsetDateTime t = JstTimes.parseRadikoDateTime("20260424050000");
        assertThat(t).isEqualTo(OffsetDateTime.parse("2026-04-24T05:00:00+09:00"));
    }
}
