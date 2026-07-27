package com.raditomo.program.service;

import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.program.repository.RawProgramXmlRepository;
import com.raditomo.radiko.program.RadikoProgramFetcher;
import com.raditomo.station.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProgramFetchServiceTest {

    private final RadikoProgramFetcher fetcher = mock(RadikoProgramFetcher.class);
    private final StationRepository stationRepository = mock(StationRepository.class);
    private final ProgramRepository programRepository = mock(ProgramRepository.class);
    private final RawProgramXmlRepository rawProgramXmlRepository = mock(RawProgramXmlRepository.class);

    private final ProgramFetchService service = new ProgramFetchService(
            fetcher, stationRepository, programRepository, rawProgramXmlRepository);

    /**
     * ラジコの XML には同一放送局・同一開始時刻の番組が重複して含まれることがある。
     * uq_programs_station_start に反する upsert を避けるため後勝ちで 1 件に寄せる。
     */
    @Test
    void fetchAndPersist_dedupesProgramsWithSameStationAndStartAt() {
        LocalDate date = LocalDate.of(2026, 8, 2);
        OffsetDateTime start = OffsetDateTime.of(2026, 8, 2, 20, 0, 0, 0, ZoneOffset.ofHours(9));

        Program first = program(start, start.plusHours(1), "カメレオンパーティー Part5");
        Program second = program(start, start.plusMinutes(30), "古城紋って一体何もん？教えて 紋ちゃん");

        when(fetcher.fetch(date, "JP13")).thenReturn(new RadikoProgramFetcher.FetchResult(
                Path.of("/tmp/20260802/JP13.xml"), 1, 0, List.of(), List.of(first, second)));
        when(programRepository.findByStationIdAndBroadcastStartAt(any(), any())).thenReturn(Optional.empty());

        ProgramFetchService.FetchAndPersistResult result = service.fetchAndPersist(date, "JP13");

        ArgumentCaptor<Program> saved = ArgumentCaptor.forClass(Program.class);
        verify(programRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("古城紋って一体何もん？教えて 紋ちゃん");
        assertThat(saved.getValue().getBroadcastEndAt()).isEqualTo(start.plusMinutes(30));
        assertThat(result.programsUpserted()).isEqualTo(1);
    }

    @Test
    void fetchAndPersist_keepsProgramsWithDifferentStartAt() {
        LocalDate date = LocalDate.of(2026, 8, 2);
        OffsetDateTime start = OffsetDateTime.of(2026, 8, 2, 20, 0, 0, 0, ZoneOffset.ofHours(9));

        when(fetcher.fetch(date, "JP13")).thenReturn(new RadikoProgramFetcher.FetchResult(
                Path.of("/tmp/20260802/JP13.xml"), 1, 0, List.of(),
                List.of(program(start, start.plusMinutes(30), "番組A"),
                        program(start.plusMinutes(30), start.plusHours(1), "番組B"))));
        when(programRepository.findByStationIdAndBroadcastStartAt(any(), any())).thenReturn(Optional.empty());

        ProgramFetchService.FetchAndPersistResult result = service.fetchAndPersist(date, "JP13");

        verify(programRepository, times(2)).save(any());
        assertThat(result.programsUpserted()).isEqualTo(2);
    }

    private Program program(OffsetDateTime start, OffsetDateTime end, String title) {
        return Program.builder()
                .stationId("NACK5")
                .broadcastStartAt(start)
                .broadcastEndAt(end)
                .broadcastDate(start.toLocalDate())
                .dayOfWeek((short) start.getDayOfWeek().getValue())
                .title(title)
                .build();
    }
}
