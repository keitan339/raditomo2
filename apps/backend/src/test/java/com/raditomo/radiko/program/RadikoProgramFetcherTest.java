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

        RadikoProgramFetcher.ParseResult result = fetcher.parse(xml, "JP13");
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
        RadikoProgramFetcher.ParseResult result = fetcher.parse(xml, "JP13");
        assertThat(result.programs()).isEmpty();
    }

    @Test
    void parse_throwsForInvalidXml() {
        assertThatThrownBy(() -> fetcher.parse("<not-xml", "JP13"))
                .isInstanceOf(RadikoProgramFetcher.RadikoProgramFetchException.class);
    }

    @Test
    void parse_acceptsUnknownAttributesAndElementsFromActualRadikoXml() {
        // 実 radiko XML には <ttl>, <srvtime>, <date>, <prog> の url/url_link/failed_record/ts_in_ng 等、
        // 多数の未マップ要素が含まれる。これらが含まれてもパースが成功し、area_id 属性が
        // 無くても引数の areaId が反映されることを確認する。
        String xml = """
                <radiko>
                  <ttl>1800</ttl>
                  <srvtime>1777511523</srvtime>
                  <stations>
                    <station id="TBS">
                      <name>TBSラジオ</name>
                      <progs>
                        <date>20260429</date>
                        <prog id="13261820" master_id="" ft="20260429050000" to="20260429063000"
                              ftl="0500" tol="0630" dur="5400">
                          <title>朝の番組</title>
                          <url>https://example.com/</url>
                          <url_link>https://example.com/?_</url_link>
                          <failed_record>0</failed_record>
                          <ts_in_ng>0</ts_in_ng>
                          <pfm>キャスター</pfm>
                          <desc></desc>
                          <info>info</info>
                        </prog>
                      </progs>
                    </station>
                  </stations>
                </radiko>
                """;
        RadikoProgramFetcher.ParseResult result = fetcher.parse(xml, "JP13");
        assertThat(result.stationsParsed()).isEqualTo(1);
        assertThat(result.stations()).extracting("areaId").containsOnly("JP13");
        assertThat(result.programs()).hasSize(1);
        assertThat(result.programs().get(0).getTitle()).isEqualTo("朝の番組");
    }
}
