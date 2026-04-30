package com.raditomo.radiko.program;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.raditomo.common.time.JstTimes;
import com.raditomo.program.entity.Program;
import com.raditomo.radiko.RadikoProperties;
import com.raditomo.station.entity.Station;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ラジコ番組表XML（v3 エリア単位）の取得・パース・ファイル保存。
 *
 * - GET https://radiko.jp/v3/program/date/{YYYYMMDD}/{areaId}.xml
 * - 放送局単位で try/catch（部分失敗は当該局のみスキップ）
 * - 生XMLは {XML_BASE_PATH}/{YYYYMMDD}/{areaId}.xml に上書き保存
 *
 * DB 永続化（programs / raw_program_xmls upsert）は呼び出し側（フェーズ4 のバッチ）で行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RadikoProgramFetcher {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // ラジコ番組表 XML には ProgramXml にマッピングしていない要素（ttl, srvtime, prog の url/failed_record 等）が
    // 多数含まれるため、未知プロパティでは失敗させない設定にする。
    private static final XmlMapper XML_MAPPER = (XmlMapper) new XmlMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final RadikoProperties props;
    private final HttpClient radikoHttpClient;

    @Value("${raditomo.xml-base-path}")
    private String xmlBasePath;

    public FetchResult fetch(LocalDate broadcastDate, String areaId) {
        String yyyymmdd = DATE_FORMAT.format(broadcastDate);
        String url = props.baseUrl() + "/v3/program/date/" + yyyymmdd + "/" + areaId + ".xml";
        String xmlContent = httpGet(url);

        Path savedFile = saveRawXml(yyyymmdd, areaId, xmlContent);
        ParseResult parsed = parse(xmlContent, areaId);

        log.info("Fetched program XML: area={} date={} stations={} programs={}",
                areaId, broadcastDate,
                parsed.stationsParsed, parsed.programs.size());
        return new FetchResult(savedFile, parsed.stationsParsed, parsed.stationsFailed,
                parsed.stations, parsed.programs);
    }

    private String httpGet(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        try {
            HttpResponse<String> resp = radikoHttpClient.send(
                    req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RadikoProgramFetchException("HTTP " + resp.statusCode() + " for " + url);
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RadikoProgramFetchException("Failed to fetch " + url, e);
        }
    }

    private Path saveRawXml(String yyyymmdd, String areaId, String content) {
        Path dir = Path.of(xmlBasePath, yyyymmdd);
        Path file = dir.resolve(areaId + ".xml");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new RadikoProgramFetchException("Failed to save raw XML: " + file, e);
        }
    }

    /** package-private for IT. */
    ParseResult parse(String xml, String requestedAreaId) {
        ProgramXml.Root root;
        try {
            root = XML_MAPPER.readValue(xml, ProgramXml.Root.class);
        } catch (Exception e) {
            throw new RadikoProgramFetchException("Failed to parse program XML", e);
        }
        if (root.stations == null || root.stations.stationList == null) {
            return new ParseResult(0, 0, List.of(), List.of());
        }

        // ラジコの新スキーマ（2026/1/26〜）では <stations> に area_id 属性が無い場合がある。
        // フォールバックとして、リクエスト時の area_id を使う。
        String areaId = root.stations.areaId != null ? root.stations.areaId : requestedAreaId;
        List<Station> stations = new ArrayList<>();
        List<Program> programs = new ArrayList<>();
        int success = 0, failed = 0;
        int order = 0;
        for (ProgramXml.Station station : root.stations.stationList) {
            try {
                stations.add(Station.builder()
                        .id(station.id)
                        .areaId(areaId)
                        .name(station.name)
                        .asciiName(station.asciiName)
                        .logoUrl(station.logo)
                        .sortOrder(order++)
                        .build());
                if (station.progs != null && station.progs.progList != null) {
                    for (ProgramXml.Prog prog : station.progs.progList) {
                        programs.add(toEntity(station.id, prog));
                    }
                }
                success++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Skipping station due to parse error: id={} cause={}", station.id, e.toString());
            }
        }
        return new ParseResult(success, failed, stations, programs);
    }

    private Program toEntity(String stationId, ProgramXml.Prog prog) {
        OffsetDateTime start = JstTimes.parseRadikoDateTime(prog.ft);
        OffsetDateTime end = JstTimes.parseRadikoDateTime(prog.to);
        LocalDate broadcastDate = JstTimes.broadcastDate(start);
        return Program.builder()
                .stationId(stationId)
                .broadcastStartAt(start)
                .broadcastEndAt(end)
                .broadcastDate(broadcastDate)
                .dayOfWeek(JstTimes.dayOfWeek(broadcastDate))
                .title(prog.title)
                .performers(prog.pfm)
                .description(prog.desc)
                .info(prog.info)
                .imageUrl(prog.img)
                .build();
    }

    public record FetchResult(Path savedFile, int stationsParsed, int stationsFailed,
                              List<Station> stations, List<Program> programs) {}

    record ParseResult(int stationsParsed, int stationsFailed, List<Station> stations, List<Program> programs) {}

    public static class RadikoProgramFetchException extends RuntimeException {
        public RadikoProgramFetchException(String msg) { super(msg); }
        public RadikoProgramFetchException(String msg, Throwable cause) { super(msg, cause); }
    }
}
