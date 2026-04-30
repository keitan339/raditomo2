package com.raditomo.radiko.download;

import com.raditomo.radiko.RadikoProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RadikoTimefreeDownloaderTest {

    @Test
    void extractMediaPlaylistUrl_returnsFirstNonCommentLine() {
        String master = """
                #EXTM3U
                #EXT-X-VERSION:6
                #EXT-X-STREAM-INF:BANDWIDTH=64000
                https://smartstream.ne.jp/path/to/media.m3u8
                """;
        String url = RadikoTimefreeDownloader.extractMediaPlaylistUrl(master, "https://radiko.jp/v2/api/ts/playlist.m3u8");
        assertThat(url).isEqualTo("https://smartstream.ne.jp/path/to/media.m3u8");
    }

    @Test
    void extractMediaPlaylistUrl_resolvesRelativeUrl() {
        String master = """
                #EXTM3U
                relative/media.m3u8
                """;
        String url = RadikoTimefreeDownloader.extractMediaPlaylistUrl(master, "https://radiko.jp/v2/api/ts/playlist.m3u8");
        assertThat(url).isEqualTo("https://radiko.jp/v2/api/ts/relative/media.m3u8");
    }

    @Test
    void extractMediaPlaylistUrl_throwsWhenNoUrl() {
        assertThatThrownBy(() -> RadikoTimefreeDownloader.extractMediaPlaylistUrl(
                "#EXTM3U\n#EXT-X-VERSION:6\n", "https://radiko.jp/x"))
                .isInstanceOf(RadikoTimefreeDownloader.RadikoDownloadException.class);
    }

    @Test
    void extractChunkUrls_preservesOrderAndSkipsComments() {
        String media = """
                #EXTM3U
                #EXT-X-VERSION:6
                #EXTINF:5.0,
                seg-001.aac
                #EXTINF:5.0,
                seg-002.aac
                #EXTINF:5.0,
                https://smartstream.ne.jp/abs/seg-003.aac
                #EXT-X-ENDLIST
                """;
        List<String> urls = RadikoTimefreeDownloader.extractChunkUrls(media, "https://smartstream.ne.jp/path/media.m3u8");
        assertThat(urls).containsExactly(
                "https://smartstream.ne.jp/path/seg-001.aac",
                "https://smartstream.ne.jp/path/seg-002.aac",
                "https://smartstream.ne.jp/abs/seg-003.aac"
        );
    }

    @Test
    void extractChunkUrls_returnsEmptyForEmptyPlaylist() {
        assertThat(RadikoTimefreeDownloader.extractChunkUrls("#EXTM3U\n#EXT-X-VERSION:6\n", "https://x")).isEmpty();
    }

    @Test
    void buildPlaylistUrl_includesAllRequiredParams() {
        RadikoProperties props = new RadikoProperties(8, "https://radiko.jp", "key", 3, 10, 30, 3);
        RadikoTimefreeDownloader d = new RadikoTimefreeDownloader(props, null, null);
        OffsetDateTime ft = OffsetDateTime.parse("2026-04-28T22:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-28T22:05:00+09:00");
        String url = d.buildPlaylistUrl(
                "https://tf-f-rpaa-radiko.smartstream.ne.jp/tf/playlist.m3u8",
                "TBS", ft, to, 300);
        assertThat(url).startsWith(
                "https://tf-f-rpaa-radiko.smartstream.ne.jp/tf/playlist.m3u8"
                        + "?station_id=TBS"
                        + "&start_at=20260428220000"
                        + "&ft=20260428220000"
                        + "&seek=20260428220000"
                        + "&end_at=20260428220500"
                        + "&to=20260428220500"
                        + "&l=300"
                        + "&lsid=");
        assertThat(url).endsWith("&type=c");
        // lsid は 32 桁の hex
        assertThat(url).matches(".*&lsid=[0-9a-f]{32}&type=c$");
    }
}
