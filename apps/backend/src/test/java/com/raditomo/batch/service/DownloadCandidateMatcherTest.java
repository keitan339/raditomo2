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
}
