package com.raditomo.program.service;

import com.raditomo.program.entity.Program;
import com.raditomo.program.entity.RawProgramXml;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.program.repository.RawProgramXmlRepository;
import com.raditomo.radiko.program.RadikoProgramFetcher;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 1日分・1エリア分の番組表を取得して DB に upsert する。
 *
 * 設計: F4 バッチが各 (放送日, area_id) の組ごとにこのサービスを呼ぶ。
 *
 * upsert 戦略: find-then-save。F4 は単一スレッド前提なので並行更新の競合は考慮しない。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgramFetchService {

    private final RadikoProgramFetcher fetcher;
    private final StationRepository stationRepository;
    private final ProgramRepository programRepository;
    private final RawProgramXmlRepository rawProgramXmlRepository;

    @Transactional
    public FetchAndPersistResult fetchAndPersist(LocalDate broadcastDate, String areaId) {
        RadikoProgramFetcher.FetchResult fetched = fetcher.fetch(broadcastDate, areaId);

        // stations upsert
        for (Station incoming : fetched.stations()) {
            Optional<Station> existing = stationRepository.findById(incoming.getId());
            Station to = existing.orElse(incoming);
            to.setAreaId(incoming.getAreaId());
            to.setName(incoming.getName());
            to.setAsciiName(incoming.getAsciiName());
            to.setLogoUrl(incoming.getLogoUrl());
            to.setSortOrder(incoming.getSortOrder());
            to.setUpdatedAt(OffsetDateTime.now(ZoneId.of("Asia/Tokyo")));
            stationRepository.save(to);
        }

        // programs upsert (station_id, broadcast_start_at)
        int upsertedPrograms = 0;
        for (Program incoming : fetched.programs()) {
            Optional<Program> existing = programRepository.findByStationIdAndBroadcastStartAt(
                    incoming.getStationId(), incoming.getBroadcastStartAt());
            Program to = existing.orElse(incoming);
            to.setBroadcastEndAt(incoming.getBroadcastEndAt());
            to.setBroadcastDate(incoming.getBroadcastDate());
            to.setDayOfWeek(incoming.getDayOfWeek());
            to.setTitle(incoming.getTitle());
            to.setPerformers(incoming.getPerformers());
            to.setDescription(incoming.getDescription());
            to.setInfo(incoming.getInfo());
            to.setImageUrl(incoming.getImageUrl());
            programRepository.save(to);
            upsertedPrograms++;
        }

        // raw_program_xmls upsert (station_id 別ではなくエリア単位ファイル管理にしているため、
        // ここでは「エリア代表」として areaId をキーに 1 行記録するのが妥当だが、テーブル定義は
        // station_id NOT NULL のため、各 station ごとに同一ファイルパスで upsert する。)
        for (Station s : fetched.stations()) {
            Optional<RawProgramXml> existing = rawProgramXmlRepository
                    .findByStationIdAndBroadcastDate(s.getId(), broadcastDate);
            RawProgramXml row = existing.orElseGet(() -> RawProgramXml.builder()
                    .stationId(s.getId())
                    .broadcastDate(broadcastDate)
                    .build());
            row.setFilePath(fetched.savedFile().toString());
            rawProgramXmlRepository.save(row);
        }

        log.info("Persisted: area={} date={} stations={} programs={}",
                areaId, broadcastDate, fetched.stations().size(), upsertedPrograms);

        return new FetchAndPersistResult(
                fetched.stations().size(),
                upsertedPrograms,
                fetched.stationsFailed());
    }

    public record FetchAndPersistResult(int stationsUpserted, int programsUpserted, int stationsFailed) {}
}
