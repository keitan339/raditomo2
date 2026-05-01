package com.raditomo.program;

import com.raditomo.AbstractIT;
import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 番組表検索でタイムフリー期限切れ（broadcast_date が today-7 より古い）の番組が
 * 結果に含まれないことを保証する。
 *
 * F4 が過去 7 日 + 当日 + 未来 7 日 = 15 日分を upsert するが、過去の F4 実行で
 * DB に積まれた古い行は削除されないため、検索クエリ側でフィルタしないと
 * もう DL できない番組が検索結果に出てきてしまう。
 */
class ProgramSearchExpiryIT extends AbstractIT {

    @Autowired ProgramRepository programRepository;
    @Autowired StationRepository stationRepository;

    private static final String AREA = "JP13";
    private static final String STATION = "TBS";

    @BeforeEach
    void seedStation() {
        programRepository.deleteAll();
        if (stationRepository.findById(STATION).isEmpty()) {
            stationRepository.save(Station.builder()
                    .id(STATION).areaId(AREA).name("TBSラジオ")
                    .sortOrder(1).updatedAt(OffsetDateTime.now())
                    .build());
        }
    }

    @Test
    void search_excludesProgramsWhoseBroadcastDateIsOlderThanOldestEligible() {
        // テスト時点を 2026-05-01 と仮定し、oldest = 2026-05-01 - 7 = 2026-04-24
        LocalDate oldestEligible = LocalDate.of(2026, 4, 24);

        // 4/23 (期限切れ) と 4/24 (ぎりぎり有効) の同タイトル番組を投入
        save("テスト番組", LocalDate.of(2026, 4, 23), 13);
        save("テスト番組", LocalDate.of(2026, 4, 24), 13);
        save("テスト番組", LocalDate.of(2026, 4, 30), 13);

        var hits = programRepository.searchByAreaAndKeyword(
                AREA, "テスト番組", oldestEligible, PageRequest.of(0, 50));

        assertThat(hits)
                .extracting(Program::getBroadcastDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 4, 24),
                        LocalDate.of(2026, 4, 30));
    }

    @Test
    void search_keywordMatch_stillFiltersByExpiry() {
        LocalDate oldestEligible = LocalDate.of(2026, 5, 1);
        save("古い番組", LocalDate.of(2026, 4, 20), 9);
        save("最新番組", LocalDate.of(2026, 5, 5), 9);

        var hits = programRepository.searchByAreaAndKeyword(
                AREA, "番組", oldestEligible, PageRequest.of(0, 50));

        assertThat(hits)
                .extracting(Program::getTitle)
                .containsExactly("最新番組");
    }

    private void save(String title, LocalDate broadcastDate, int hour) {
        OffsetDateTime start = broadcastDate.atTime(hour, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        programRepository.save(Program.builder()
                .stationId(STATION)
                .broadcastStartAt(start)
                .broadcastEndAt(start.plusHours(1))
                .broadcastDate(broadcastDate)
                .dayOfWeek((short) ((broadcastDate.getDayOfWeek().getValue()) % 7))
                .title(title)
                .build());
    }
}
