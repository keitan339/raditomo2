package com.raditomo.radiko;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.raditomo.AbstractIT;
import com.raditomo.radiko.auth.RadikoAuthService;
import com.raditomo.radiko.auth.RadikoAuthToken;
import com.raditomo.radiko.download.RadikoTimefreeDownloader;
import com.raditomo.radiko.program.RadikoProgramFetcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "raditomo.radiko.auth-key=0123456789abcdef0123456789abcdef01234567",
        "raditomo.radiko.auth-cache-hours=0"
})
class RadikoIT extends AbstractIT {

    static final WireMockServer WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired RadikoAuthService authService;
    @Autowired RadikoProgramFetcher programFetcher;
    @Autowired RadikoTimefreeDownloader downloader;

    @TempDir Path tempXmlDir;

    @BeforeAll
    static void startWireMock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("raditomo.radiko.base-url", () -> WIREMOCK.baseUrl());
    }

    @BeforeEach
    void resetMocks() {
        WIREMOCK.resetAll();
        // ベースの auth 応答を毎回再登録（authService 内部キャッシュは TTL=0 で常に再取得）
        WIREMOCK.stubFor(get(urlEqualTo("/v2/api/auth1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Radiko-AuthToken", "stub-token")
                        .withHeader("X-Radiko-KeyOffset", "5")
                        .withHeader("X-Radiko-KeyLength", "10")));
        WIREMOCK.stubFor(get(urlEqualTo("/v2/api/auth2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("JP13,東京都,Tokyo")));
    }

    @Test
    void auth_returnsAreaIdFromAuth2() {
        RadikoAuthToken token = authService.refresh();
        assertThat(token.authToken()).isEqualTo("stub-token");
        assertThat(token.areaId()).isEqualTo("JP13");
        assertThat(token.areaName()).isEqualTo("東京都");

        WIREMOCK.verify(getRequestedFor(urlEqualTo("/v2/api/auth1")));
        WIREMOCK.verify(getRequestedFor(urlEqualTo("/v2/api/auth2"))
                .withHeader("X-Radiko-AuthToken", equalTo("stub-token"))
                .withHeader("X-Radiko-Partialkey", matching("[A-Za-z0-9+/=]+")));
    }

    @Test
    void programFetcher_persistsXmlAndParses() {
        String xml = """
                <radiko>
                  <stations area_id="JP13">
                    <station id="TBS">
                      <progs>
                        <prog ft="20260424050000" to="20260424080000" dur="10800">
                          <title>朝の番組</title>
                          <pfm>キャスター</pfm>
                        </prog>
                      </progs>
                    </station>
                  </stations>
                </radiko>
                """;
        WIREMOCK.stubFor(get(urlEqualTo("/v3/program/date/20260424/JP13.xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=utf-8")
                        .withBody(xml)));

        // xml-base-path を一時ディレクトリへ向ける
        org.springframework.test.util.ReflectionTestUtils.setField(
                programFetcher, "xmlBasePath", tempXmlDir.toString());

        RadikoProgramFetcher.FetchResult result = programFetcher.fetch(
                LocalDate.of(2026, 4, 24), "JP13");

        assertThat(result.programs()).hasSize(1);
        assertThat(result.programs().get(0).getTitle()).isEqualTo("朝の番組");
        assertThat(Files.exists(tempXmlDir.resolve("20260424/JP13.xml"))).isTrue();
    }

    @Test
    void downloader_concatenatesAllChunksInOrder(@TempDir Path tmp) {
        String master = "#EXTM3U\n" + WIREMOCK.baseUrl() + "/media.m3u8\n";
        String media = """
                #EXTM3U
                #EXTINF:5.0,
                seg-001.aac
                #EXTINF:5.0,
                seg-002.aac
                #EXTINF:5.0,
                seg-003.aac
                #EXT-X-ENDLIST
                """;
        WIREMOCK.stubFor(get(urlMatching("/v2/api/ts/playlist\\.m3u8.*"))
                .willReturn(aResponse().withStatus(200).withBody(master)));
        WIREMOCK.stubFor(get(urlEqualTo("/media.m3u8"))
                .willReturn(aResponse().withStatus(200).withBody(media)));
        WIREMOCK.stubFor(get(urlEqualTo("/seg-001.aac"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 1, 1})));
        WIREMOCK.stubFor(get(urlEqualTo("/seg-002.aac"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{2, 2, 2})));
        WIREMOCK.stubFor(get(urlEqualTo("/seg-003.aac"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{3, 3, 3})));

        Path output = tmp.resolve("merged.aac");
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-24T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-24T05:00:15+09:00");

        RadikoTimefreeDownloader.DownloadStats stats = downloader.download("TBS", ft, to, output);

        assertThat(stats.chunkCount()).isEqualTo(3);
        assertThat(stats.totalBytes()).isEqualTo(9);
        try {
            byte[] merged = Files.readAllBytes(output);
            // 順序通り連結されていることを検証
            assertThat(merged).containsExactly(1, 1, 1, 2, 2, 2, 3, 3, 3);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void downloader_retriesAndRecovers(@TempDir Path tmp) {
        String master = "#EXTM3U\n" + WIREMOCK.baseUrl() + "/media.m3u8\n";
        String media = "#EXTM3U\n#EXTINF:5.0,\nseg-001.aac\n#EXT-X-ENDLIST\n";
        WIREMOCK.stubFor(get(urlMatching("/v2/api/ts/playlist\\.m3u8.*"))
                .willReturn(aResponse().withStatus(200).withBody(master)));
        WIREMOCK.stubFor(get(urlEqualTo("/media.m3u8"))
                .willReturn(aResponse().withStatus(200).withBody(media)));
        WIREMOCK.stubFor(get(urlEqualTo("/seg-001.aac"))
                .inScenario("retry-once")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("retried"));
        WIREMOCK.stubFor(get(urlEqualTo("/seg-001.aac"))
                .inScenario("retry-once")
                .whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{9, 9})));

        Path output = tmp.resolve("merged.aac");
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-24T05:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-24T05:00:05+09:00");

        RadikoTimefreeDownloader.DownloadStats stats = downloader.download("TBS", ft, to, output);
        assertThat(stats.chunkCount()).isEqualTo(1);
        assertThat(stats.totalBytes()).isEqualTo(2);
    }
}
