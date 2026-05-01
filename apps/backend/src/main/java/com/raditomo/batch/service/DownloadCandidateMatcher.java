package com.raditomo.batch.service;

import com.raditomo.common.time.JstTimes;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F2 動的照合: 登録 × 番組表 × 履歴 → ダウンロード対象を導出する。
 *
 * 設計参照: docs/requirements/01-functional-requirements.md F2、docs/design/01-database-design.md
 *
 * 共通条件:
 * - 放送が完了している（broadcast_end_at <= now）
 * - 履歴に SUCCESS がない（force=true なら無視）
 * - 登録が ACTIVE
 *
 * ONCE:
 * - station_id + broadcast_start_at で番組を引き、上記条件を満たすなら候補
 *
 * WEEKLY: タイムフリー対象期間内（過去 7 日 + 当日）の broadcast_date を列挙し、
 *         登録の day_of_week と一致する各日に対して下記 4 ステップを適用する。
 *         同じ登録から複数週分の候補が返り得る。
 *  1. 登録時の "時間帯パターン" を当該日に当てはめた時刻 (expectedStart) で
 *     program を引き、title が登録と一致 → 候補
 *  2. 一致しない場合、同放送日内で同 title の program を検索 → 候補（時刻はその program の値）
 *  3. それでも見つからない場合、expectedStart 時刻の program があれば
 *     title が違っても DL（特番・別番組も DB 上の番組名で履歴記録、
 *     {@code project_weekly_matching.md} 参照）。program が無ければ登録時の情報で強制 DL。
 *
 * date 指定時は broadcast_date でフィルタ。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadCandidateMatcher {

    /** タイムフリー対象期間: 過去 7 日 + 当日（5:00 区切りの放送日基準）。 */
    private static final int TIMEFREE_PAST_DAYS = 7;

    private final DownloadRegistrationRepository registrationRepository;
    private final ProgramRepository programRepository;
    private final DownloadHistoryRepository historyRepository;
    private final Clock clock;

    /**
     * @param targetDate 放送日でフィルタする場合は指定。null なら全範囲
     * @param force      true なら履歴 SUCCESS チェックをスキップ
     */
    public List<DownloadCandidate> findCandidates(LocalDate targetDate, boolean force) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DownloadRegistration> active = registrationRepository.findByStatus(RegistrationStatus.ACTIVE);
        return matchAll(active, targetDate, force, now);
    }

    /** package-private: テストから直接登録リストを渡せるようにする。 */
    List<DownloadCandidate> matchAll(List<DownloadRegistration> registrations,
                                     LocalDate targetDate,
                                     boolean force,
                                     OffsetDateTime now) {
        List<DownloadCandidate> candidates = new ArrayList<>();
        for (DownloadRegistration r : registrations) {
            try {
                candidates.addAll(match(r, targetDate, force, now));
            } catch (RuntimeException e) {
                log.warn("Match failed for registration id={}: {}", r.getId(), e.toString());
            }
        }
        return candidates;
    }

    private List<DownloadCandidate> match(DownloadRegistration r, LocalDate targetDate,
                                          boolean force, OffsetDateTime now) {
        if (r.getRegistrationType() == RegistrationType.ONCE) {
            return matchOnce(r, targetDate, force, now)
                    .map(List::of).orElseGet(List::of);
        }
        return matchWeekly(r, targetDate, force, now);
    }

    private Optional<DownloadCandidate> matchOnce(DownloadRegistration r, LocalDate targetDate,
                                                  boolean force, OffsetDateTime now) {
        Optional<Program> program = programRepository.findByStationIdAndBroadcastStartAt(
                r.getStationId(), r.getBroadcastStartAt());
        if (program.isEmpty()) return Optional.empty();
        Program p = program.get();
        if (!matchesTargetDate(p.getBroadcastDate(), targetDate)) return Optional.empty();
        if (!isFinishedAndAvailable(p, now, force, r.getUserId())) return Optional.empty();
        return Optional.of(new DownloadCandidate(
                r.getId(), r.getUserId(), p.getStationId(),
                p.getTitle(), p.getPerformers(),
                p.getBroadcastStartAt(), p.getBroadcastEndAt(),
                RegistrationType.ONCE, "once"));
    }

    /**
     * WEEKLY: タイムフリー期間内の各 broadcast_date について day_of_week 一致を判定し、
     * 一致する各日に対してステップ 1〜4 を適用する。複数週分の候補を返し得る。
     */
    private List<DownloadCandidate> matchWeekly(DownloadRegistration r, LocalDate targetDate,
                                                boolean force, OffsetDateTime now) {
        if (r.getDayOfWeek() == null) {
            log.warn("WEEKLY registration without dayOfWeek: id={}", r.getId());
            return List.of();
        }
        List<DownloadCandidate> result = new ArrayList<>();
        LocalDate today = JstTimes.broadcastDate(now);
        LocalDate oldest = today.minusDays(TIMEFREE_PAST_DAYS);

        for (LocalDate date = oldest; !date.isAfter(today); date = date.plusDays(1)) {
            if (!matchesTargetDate(date, targetDate)) continue;
            if (JstTimes.dayOfWeek(date) != r.getDayOfWeek().shortValue()) continue;
            matchWeeklyForDate(r, date, force, now).ifPresent(result::add);
        }
        return result;
    }

    private Optional<DownloadCandidate> matchWeeklyForDate(DownloadRegistration r,
                                                           LocalDate broadcastDate,
                                                           boolean force,
                                                           OffsetDateTime now) {
        OffsetDateTime expectedStart = composeStart(r.getBroadcastStartAt(), broadcastDate);

        // ステップ 1+2: 同時刻同タイトル
        Optional<Program> exact = programRepository.findByStationIdAndBroadcastStartAt(
                r.getStationId(), expectedStart);
        if (exact.isPresent() && r.getTitle().equals(exact.get().getTitle())) {
            Program p = exact.get();
            if (isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-1"));
            }
        }

        // ステップ 3: 同放送日同タイトル
        Optional<Program> sameDateSameTitle = programRepository
                .findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                        r.getStationId(), broadcastDate, r.getTitle());
        if (sameDateSameTitle.isPresent()) {
            Program p = sameDateSameTitle.get();
            if (isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-3"));
            }
        }

        // ステップ 4: title 不一致でも DL（DB 上の番組名で履歴記録）。
        if (exact.isPresent()) {
            Program p = exact.get();
            if (isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-4"));
            }
        } else {
            // 番組表に該当時間帯の番組がない → 登録時の情報で強制 DL
            OffsetDateTime expectedEnd = expectedStart.plus(
                    Duration.between(r.getBroadcastStartAt(), r.getBroadcastEndAt()));
            if (!expectedEnd.isBefore(now)) return Optional.empty();
            if (!force && hasSuccess(r.getUserId(), r.getStationId(), expectedStart)) {
                return Optional.empty();
            }
            return Optional.of(new DownloadCandidate(
                    r.getId(), r.getUserId(), r.getStationId(),
                    r.getTitle(), null,
                    expectedStart, expectedEnd,
                    RegistrationType.WEEKLY, "weekly-4"));
        }
        return Optional.empty();
    }

    /**
     * 登録時の broadcast_start_at の "時間帯パターン" (時刻 + 5:00区切り放送日からの日オフセット) を
     * 別の broadcast_date に当てはめて期待される start を返す。
     *
     * 例:
     *   reg=2026-04-23T13:00 JST (broadcastDate=4/23, time=13:00, 同日)
     *   target=2026-04-30 → 2026-04-30T13:00 JST
     *
     *   reg=2026-04-30T01:00 JST (broadcastDate=4/29, time=01:00, 翌カレンダー日)
     *   target=2026-05-06 → 2026-05-07T01:00 JST （翌カレンダー日 1:00）
     */
    private static OffsetDateTime composeStart(OffsetDateTime registrationStart, LocalDate targetBroadcastDate) {
        ZoneId jst = JstTimes.JST;
        LocalDate registrationCalendarDate = registrationStart.atZoneSameInstant(jst).toLocalDate();
        LocalDate registrationBroadcastDate = JstTimes.broadcastDate(registrationStart);
        long calendarDayOffset = registrationCalendarDate.toEpochDay() - registrationBroadcastDate.toEpochDay();
        LocalTime timeOfDay = registrationStart.atZoneSameInstant(jst).toLocalTime();
        LocalDate targetCalendarDate = targetBroadcastDate.plusDays(calendarDayOffset);
        return targetCalendarDate.atTime(timeOfDay).atZone(jst).toOffsetDateTime();
    }

    private DownloadCandidate toCandidate(DownloadRegistration r, Program p, String step) {
        return new DownloadCandidate(
                r.getId(), r.getUserId(), p.getStationId(),
                p.getTitle(), p.getPerformers(),
                p.getBroadcastStartAt(), p.getBroadcastEndAt(),
                RegistrationType.WEEKLY, step);
    }

    private boolean matchesTargetDate(LocalDate broadcastDate, LocalDate targetDate) {
        return targetDate == null || broadcastDate.equals(targetDate);
    }

    private boolean isFinishedAndAvailable(Program p, OffsetDateTime now, boolean force, Long userId) {
        if (!p.getBroadcastEndAt().isBefore(now) && !p.getBroadcastEndAt().isEqual(now)) return false;
        if (!force && hasSuccess(userId, p.getStationId(), p.getBroadcastStartAt())) return false;
        return true;
    }

    private boolean hasSuccess(Long userId, String stationId, OffsetDateTime startAt) {
        return historyRepository.findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                userId, stationId, startAt, DownloadStatus.SUCCESS).isPresent();
    }
}
