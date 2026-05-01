package com.raditomo.batch.service;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadCandidateMatcherTest {

    private DownloadRegistrationRepository registrationRepository;
    private ProgramRepository programRepository;
    private DownloadHistoryRepository historyRepository;
    private Clock clock;
    private DownloadCandidateMatcher matcher;

    @BeforeEach
    void setup() {
        registrationRepository = mock(DownloadRegistrationRepository.class);
        programRepository = mock(ProgramRepository.class);
        historyRepository = mock(DownloadHistoryRepository.class);
        clock = Clock.fixed(Instant.parse("2026-04-26T00:00:00Z"), ZoneOffset.UTC); // 2026-04-26 09:00 JST
        matcher = new DownloadCandidateMatcher(
                registrationRepository, programRepository, historyRepository, clock);
    }

    private DownloadRegistration onceReg(long id, String station, OffsetDateTime ft, OffsetDateTime to, String title) {
        return DownloadRegistration.builder()
                .id(id).userId(1L).stationId(station)
                .registrationType(RegistrationType.ONCE)
                .title(title)
                .broadcastStartAt(ft).broadcastEndAt(to)
                .status(RegistrationStatus.ACTIVE).build();
    }

    private DownloadRegistration weeklyReg(long id, String station, OffsetDateTime ft, OffsetDateTime to,
                                           String title, short dow) {
        return DownloadRegistration.builder()
                .id(id).userId(1L).stationId(station)
                .registrationType(RegistrationType.WEEKLY)
                .title(title)
                .broadcastStartAt(ft).broadcastEndAt(to)
                .dayOfWeek(dow)
                .status(RegistrationStatus.ACTIVE).build();
    }

    private Program program(String station, OffsetDateTime ft, OffsetDateTime to,
                            LocalDate broadcastDate, String title, String performers) {
        return Program.builder()
                .stationId(station)
                .broadcastStartAt(ft).broadcastEndAt(to)
                .broadcastDate(broadcastDate)
                .title(title).performers(performers).build();
    }

    @Test
    void once_finishedProgram_noHistory_yieldsCandidate() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T08:00:00+09:00");
        DownloadRegistration r = onceReg(10L, "TBS", ft, to, "朝のニュース");
        Program p = program("TBS", ft, to, LocalDate.of(2026, 4, 25), "朝のニュース", "出演者A");

        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", ft)).thenReturn(Optional.of(p));
        when(historyRepository.findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                eq(1L), eq("TBS"), eq(ft), eq(DownloadStatus.SUCCESS))).thenReturn(Optional.empty());

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).matchStep()).isEqualTo("once");
        assertThat(result.get(0).title()).isEqualTo("朝のニュース");
    }

    @Test
    void once_skipsWhenSuccessHistoryExists() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T08:00:00+09:00");
        DownloadRegistration r = onceReg(10L, "TBS", ft, to, "朝のニュース");
        Program p = program("TBS", ft, to, LocalDate.of(2026, 4, 25), "朝のニュース", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", ft)).thenReturn(Optional.of(p));
        when(historyRepository.findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                eq(1L), eq("TBS"), eq(ft), eq(DownloadStatus.SUCCESS)))
                .thenReturn(Optional.of(DownloadHistory.builder().id(99L).build()));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).isEmpty();
    }

    @Test
    void once_force_redownloadsEvenWithSuccessHistory() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T08:00:00+09:00");
        DownloadRegistration r = onceReg(10L, "TBS", ft, to, "朝のニュース");
        Program p = program("TBS", ft, to, LocalDate.of(2026, 4, 25), "朝のニュース", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", ft)).thenReturn(Optional.of(p));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, true,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).hasSize(1);
    }

    @Test
    void once_skipsBeforeBroadcastEnd() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-26T10:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-26T12:00:00+09:00");
        DownloadRegistration r = onceReg(10L, "TBS", ft, to, "x");
        Program p = program("TBS", ft, to, LocalDate.of(2026, 4, 26), "x", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", ft)).thenReturn(Optional.of(p));

        // now = 09:00 JST、放送終了 12:00 → まだ終わっていない
        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).isEmpty();
    }

    @Test
    void weekly_step1_titleMatchesAtRegisteredTime() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T22:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T23:00:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "QRR", ft, to, "週末番組", (short) 6);
        Program p = program("QRR", ft, to, LocalDate.of(2026, 4, 25), "週末番組", "出演者X");
        when(programRepository.findByStationIdAndBroadcastStartAt("QRR", ft)).thenReturn(Optional.of(p));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-1");
            assertThat(c.title()).isEqualTo("週末番組");
        });
    }

    @Test
    void weekly_step3_findsByTitleSameDayDifferentTime() {
        OffsetDateTime registeredFt = OffsetDateTime.parse("2026-04-25T22:00:00+09:00");
        OffsetDateTime registeredTo = OffsetDateTime.parse("2026-04-25T23:00:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "QRR", registeredFt, registeredTo, "週末番組", (short) 6);

        // 登録時刻には別番組
        Program other = program("QRR", registeredFt, registeredTo, LocalDate.of(2026, 4, 25), "別番組", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("QRR", registeredFt))
                .thenReturn(Optional.of(other));

        // 同じ放送日内で title=週末番組 が別時刻に存在
        OffsetDateTime moved = OffsetDateTime.parse("2026-04-25T20:00:00+09:00");
        Program found = program("QRR", moved, moved.plusHours(1), LocalDate.of(2026, 4, 25), "週末番組", "出演者");
        when(programRepository.findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                eq("QRR"), eq(LocalDate.of(2026, 4, 25)), eq("週末番組"))).thenReturn(Optional.of(found));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-3");
            assertThat(c.broadcastStartAt()).isEqualTo(moved);
            assertThat(c.title()).isEqualTo("週末番組");
        });
    }

    @Test
    void weekly_step4_titleMissing_downloadsWithDbTitle() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T22:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T23:00:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "QRR", ft, to, "週末番組", (short) 6);

        // 登録時刻には特番が放送されている
        Program special = program("QRR", ft, to, LocalDate.of(2026, 4, 25), "緊急特別番組", "報道部");
        when(programRepository.findByStationIdAndBroadcastStartAt("QRR", ft)).thenReturn(Optional.of(special));
        // 同放送日に title=週末番組 は存在しない
        when(programRepository.findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                eq("QRR"), eq(LocalDate.of(2026, 4, 25)), eq("週末番組"))).thenReturn(Optional.empty());

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));

        // 別番組でもDLする（手順4）。DB上の番組名で履歴記録するため title は "緊急特別番組"
        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-4");
            assertThat(c.title()).isEqualTo("緊急特別番組");
            assertThat(c.broadcastStartAt()).isEqualTo(ft);
        });
    }

    @Test
    void weekly_step4_noProgramAtTime_usesRegisteredInfo() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T22:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T23:00:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "QRR", ft, to, "週末番組", (short) 6);

        when(programRepository.findByStationIdAndBroadcastStartAt("QRR", ft)).thenReturn(Optional.empty());
        when(programRepository.findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                eq("QRR"), eq(LocalDate.of(2026, 4, 25)), eq("週末番組"))).thenReturn(Optional.empty());

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-4");
            assertThat(c.title()).isEqualTo("週末番組");
        });
    }

    @Test
    void targetDate_filtersOutOtherDates() {
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-25T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-25T08:00:00+09:00");
        DownloadRegistration r = onceReg(10L, "TBS", ft, to, "x");
        Program p = program("TBS", ft, to, LocalDate.of(2026, 4, 25), "x", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", ft)).thenReturn(Optional.of(p));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r),
                LocalDate.of(2026, 4, 24), false,
                OffsetDateTime.parse("2026-04-26T09:00:00+09:00"));
        assertThat(result).isEmpty();
    }

    /**
     * WEEKLY: 登録時の broadcastDate と異なる週の同曜日も、タイムフリー期間内に
     * あれば候補化される（実運用での週次反復）。
     */
    @Test
    void weekly_iteratesAcrossWeeks_returnsAllUndownloadedAirings() {
        // 登録: FMT 木曜 13:00 「山崎怜奈」, broadcastDate=4/23 (Thu)
        OffsetDateTime regFt = OffsetDateTime.parse("2026-04-23T13:00:00+09:00");
        OffsetDateTime regTo = OffsetDateTime.parse("2026-04-23T14:55:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "FMT", regFt, regTo, "山崎怜奈", (short) 4); // Thu

        // now = 2026-05-01 10:12 JST → broadcastDate=5/1 (Fri)。past 7 = 4/24..5/1
        // 木曜は 4/30 のみ（4/23 は範囲外）
        OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:12:00+09:00");

        OffsetDateTime apr30Ft = OffsetDateTime.parse("2026-04-30T13:00:00+09:00");
        OffsetDateTime apr30To = OffsetDateTime.parse("2026-04-30T14:55:00+09:00");
        Program apr30 = program("FMT", apr30Ft, apr30To, LocalDate.of(2026, 4, 30), "山崎怜奈", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("FMT", apr30Ft))
                .thenReturn(Optional.of(apr30));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false, now);

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-1");
            assertThat(c.broadcastStartAt()).isEqualTo(apr30Ft);
        });
    }

    /**
     * WEEKLY: 登録時の broadcastDate も範囲内なら、その週の airing も候補に入る。
     * （ただし履歴 SUCCESS があればスキップされる経路を別テストで担保）
     */
    @Test
    void weekly_includesOriginalDateAiringWhenInRange() {
        // 登録: TBS 金曜 11:30, broadcastDate=4/24 (Fri)
        OffsetDateTime regFt = OffsetDateTime.parse("2026-04-24T11:30:00+09:00");
        OffsetDateTime regTo = OffsetDateTime.parse("2026-04-24T11:55:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "TBS", regFt, regTo, "ほっとひといき", (short) 5); // Fri

        // now = 2026-04-30 10:00 JST → broadcastDate=4/30。past 7 = 4/23..4/30
        // 金曜は 4/24 のみ
        OffsetDateTime now = OffsetDateTime.parse("2026-04-30T10:00:00+09:00");

        Program apr24 = program("TBS", regFt, regTo, LocalDate.of(2026, 4, 24), "ほっとひといき", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("TBS", regFt))
                .thenReturn(Optional.of(apr24));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false, now);

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-1");
            assertThat(c.broadcastStartAt()).isEqualTo(regFt);
        });
    }

    /**
     * WEEKLY: 深夜放送（5:00区切りで前日扱い）の時間帯パターンが翌週も維持される。
     * 登録 broadcast_start_at=4/30 01:00 (broadcastDate=4/29 Wed) →
     * 次週 broadcastDate=5/6 (Wed) の airing は 5/7 01:00。
     */
    @Test
    void weekly_lateNightTimePattern_preservedAcrossWeeks() {
        // 登録: LFR 水曜深夜 01:00, broadcastDate=4/29 (Wed)
        OffsetDateTime regFt = OffsetDateTime.parse("2026-04-30T01:00:00+09:00");
        OffsetDateTime regTo = OffsetDateTime.parse("2026-04-30T03:00:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "LFR", regFt, regTo, "オールナイトニッポン", (short) 3); // Wed

        // now = 2026-05-07 10:00 JST → broadcastDate=5/7。past 7 = 4/30..5/7
        // 水曜は 5/6 のみ（4/29 は範囲外）
        OffsetDateTime now = OffsetDateTime.parse("2026-05-07T10:00:00+09:00");

        // 次週 broadcastDate=5/6 → 翌カレンダー日 5/7 01:00
        OffsetDateTime nextFt = OffsetDateTime.parse("2026-05-07T01:00:00+09:00");
        OffsetDateTime nextTo = OffsetDateTime.parse("2026-05-07T03:00:00+09:00");
        Program nextProg = program("LFR", nextFt, nextTo, LocalDate.of(2026, 5, 6), "オールナイトニッポン", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("LFR", nextFt))
                .thenReturn(Optional.of(nextProg));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false, now);

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.matchStep()).isEqualTo("weekly-1");
            assertThat(c.broadcastStartAt()).isEqualTo(nextFt);
        });
    }

    /**
     * WEEKLY: タイムフリー期間外（8 日以上前）の airing は候補にならない。
     */
    @Test
    void weekly_skipsAiringsOlderThanTimefreeWindow() {
        // 登録: 元 broadcastDate=4/23 (Thu), 次回 4/30 (Thu)
        OffsetDateTime regFt = OffsetDateTime.parse("2026-04-23T13:00:00+09:00");
        OffsetDateTime regTo = OffsetDateTime.parse("2026-04-23T14:55:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "FMT", regFt, regTo, "山崎怜奈", (short) 4);

        // now = 2026-05-08 10:00 JST → broadcastDate=5/8。past 7 = 5/1..5/8
        // 木曜は 5/7 のみ（4/30 はちょうど範囲外）
        OffsetDateTime now = OffsetDateTime.parse("2026-05-08T10:00:00+09:00");

        OffsetDateTime may7Ft = OffsetDateTime.parse("2026-05-07T13:00:00+09:00");
        OffsetDateTime may7To = OffsetDateTime.parse("2026-05-07T14:55:00+09:00");
        Program may7 = program("FMT", may7Ft, may7To, LocalDate.of(2026, 5, 7), "山崎怜奈", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("FMT", may7Ft))
                .thenReturn(Optional.of(may7));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false, now);

        // 4/30 は出ない（番組未モックでも、そもそも範囲外なので呼ばれない）
        assertThat(result).extracting(DownloadCandidate::broadcastStartAt)
                .containsExactly(may7Ft);
    }

    /**
     * WEEKLY: 履歴 SUCCESS がある週はスキップ、無い週は候補化（複数週共存パターン）。
     */
    @Test
    void weekly_skipsWeeksWithSuccessHistory_keepsOthers() {
        OffsetDateTime regFt = OffsetDateTime.parse("2026-04-23T13:00:00+09:00");
        OffsetDateTime regTo = OffsetDateTime.parse("2026-04-23T14:55:00+09:00");
        DownloadRegistration r = weeklyReg(20L, "FMT", regFt, regTo, "山崎怜奈", (short) 4);

        // now = 2026-05-07 18:00 JST → broadcastDate=5/7。past 7 = 4/30..5/7
        // 木曜は 4/30, 5/7。両方とも放送終了済み。
        OffsetDateTime now = OffsetDateTime.parse("2026-05-07T18:00:00+09:00");

        OffsetDateTime apr30Ft = OffsetDateTime.parse("2026-04-30T13:00:00+09:00");
        OffsetDateTime apr30To = OffsetDateTime.parse("2026-04-30T14:55:00+09:00");
        Program apr30 = program("FMT", apr30Ft, apr30To, LocalDate.of(2026, 4, 30), "山崎怜奈", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("FMT", apr30Ft))
                .thenReturn(Optional.of(apr30));

        OffsetDateTime may7Ft = OffsetDateTime.parse("2026-05-07T13:00:00+09:00");
        OffsetDateTime may7To = OffsetDateTime.parse("2026-05-07T14:55:00+09:00");
        Program may7 = program("FMT", may7Ft, may7To, LocalDate.of(2026, 5, 7), "山崎怜奈", null);
        when(programRepository.findByStationIdAndBroadcastStartAt("FMT", may7Ft))
                .thenReturn(Optional.of(may7));

        // 4/30 だけ既にダウンロード済み
        when(historyRepository.findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                eq(1L), eq("FMT"), eq(apr30Ft), eq(DownloadStatus.SUCCESS)))
                .thenReturn(Optional.of(DownloadHistory.builder().id(99L).build()));

        List<DownloadCandidate> result = matcher.matchAll(List.of(r), null, false, now);

        assertThat(result).extracting(DownloadCandidate::broadcastStartAt)
                .containsExactly(may7Ft);
    }
}
