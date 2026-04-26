package com.raditomo.radiko.program;

import com.raditomo.program.entity.Program;
import com.raditomo.radiko.RadikoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;

class RadikoProgramFetcherTest {

    private RadikoProgramFetcher fetcher;

    @BeforeEach
    void setup() {
        RadikoProperties props = new RadikoProperties(8, "https://radiko.jp", "key", 3, 10, 30, 3);
        fetcher = new RadikoProgramFetcher(props, null);
    }

    @Test
    void parse_extractsProgramsAcrossStations() {
        String xml = """
                <radiko>
                  <stations area_id="JP13">
                    <station id="TBS">
                      <name>TBSラジオ</name>
                      <progs>
                        <prog ft="20260424050000" to="20260424080000" dur="10800">
                          <title>朝のニュース</title>
                          <pfm>キャスターA</pfm>
                          <desc>朝のニュース番組</desc>
                          <info>info</info>
                          <img>http://example.com/p.png</img>
                        </prog>
                        <prog ft="20260424080000" to="20260424100000" dur="7200">
                          <title>朝ワイド</title>
                          <pfm>パーソナリティB</pfm>
                        </prog>
                      </progs>
                    </station>
                    <station id="QRR">
                      <name>文化放送</name>
                      <progs>
                        <prog ft="20260425010000" to="20260425030000" dur="7200">
                          <title>深夜番組</title>
                          <pfm>パーソナリティC</pfm>
                        </prog>
                      </progs>
                    </station>
                  </stations>
                </radiko>
                """;

        RadikoProgramFetcher.ParseResult result = fetcher.parse(xml);
        assertThat(result.stationsParsed()).isEqualTo(2);
        assertThat(result.stationsFailed()).isEqualTo(0);
        assertThat(result.stations()).extracting("id").containsExactly("TBS", "QRR");
        assertThat(result.stations()).extracting("areaId").containsOnly("JP13");
        assertThat(result.programs()).hasSize(3);

        Program first = result.programs().get(0);
        assertThat(first.getStationId()).isEqualTo("TBS");
        assertThat(first.getTitle()).isEqualTo("朝のニュース");
        assertThat(first.getBroadcastStartAt())
                .isEqualTo(OffsetDateTime.parse("2026-04-24T05:00:00+09:00"));
        assertThat(first.getBroadcastDate()).isEqualTo(LocalDate.of(2026, 4, 24));
        assertThat(first.getPerformers()).isEqualTo("キャスターA");
        assertThat(first.getImageUrl()).isEqualTo("http://example.com/p.png");

        Program lateNight = result.programs().get(2);
        assertThat(lateNight.getStationId()).isEqualTo("QRR");
        // 深夜 1:00 → 放送日は前日 4/24
        assertThat(lateNight.getBroadcastDate()).isEqualTo(LocalDate.of(2026, 4, 24));
        assertThat(lateNight.getDayOfWeek()).isEqualTo((short) 5); // 4/24 は金曜
    }

    @Test
    void parse_returnsEmptyForMissingStations() {
        String xml = "<radiko></radiko>";
        RadikoProgramFetcher.ParseResult result = fetcher.parse(xml);
        assertThat(result.programs()).isEmpty();
    }

    @Test
    void parse_throwsForInvalidXml() {
        assertThatThrownBy(() -> fetcher.parse("<not-xml"))
                .isInstanceOf(RadikoProgramFetcher.RadikoProgramFetchException.class);
    }
}
