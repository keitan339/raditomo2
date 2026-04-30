package com.raditomo.history.service;

import com.raditomo.history.RecordingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingTitleGrouperTest {

    /** application.yml と同じデフォルトパターン。 */
    private static final List<String> DEFAULT_PATTERNS = List.of(
            "[\\s　]*[0-9０-９]{1,2}時台.*$",
            "[\\s　]+Part\\s*[0-9０-９]+\\s*$",
            "[\\s　]*[\\(（][0-9０-９]+[\\)）]\\s*$",
            "[\\s　]*第[0-9０-９]+回\\s*$");

    private RecordingTitleGrouper newGrouper(List<String> patterns) {
        return new RecordingTitleGrouper(new RecordingProperties(patterns));
    }

    @Test
    void hourBandIsStripped() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("らじらー！　サンデー　8時台 山田・佐藤"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　9時台 田中"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　10時台"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void hourBandWithFullwidthDigits() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("らじらー！　サンデー　１０時台　五百城茉央　黒見明香（乃木坂４６）"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　９時台　五百城茉央"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　８時台　五百城茉央"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void parenthesizedPartNumIsStripped() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("パンサー向井の#ふらっと (1)"))
                .isEqualTo("パンサー向井の#ふらっと");
        assertThat(g.groupKey("番組A（2）"))
                .isEqualTo("番組A");
    }

    @Test
    void partNumIsStripped() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("番組B Part 2"))
                .isEqualTo("番組B");
        assertThat(g.groupKey("番組B　Part3"))
                .isEqualTo("番組B");
    }

    @Test
    void kaiSuffixIsStripped() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("番組C 第3回")).isEqualTo("番組C");
    }

    @Test
    void titleWithoutSuffixIsUnchanged() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey("オールナイトニッポン"))
                .isEqualTo("オールナイトニッポン");
    }

    @Test
    void emptyPatternListReturnsTrimmedTitle() {
        // 設定が空なら何も削除しない（trim だけ）
        var g = newGrouper(List.of());
        assertThat(g.groupKey("番組  ")).isEqualTo("番組");
    }

    @Test
    void invalidRegexIsSkippedAndOthersStillApply() {
        // 不正な正規表現はスキップされ、残りのパターンは効く
        var g = newGrouper(List.of("[invalid(", "[\\s　]+Part\\s*\\d+\\s*$"));
        assertThat(g.groupKey("番組X Part 5")).isEqualTo("番組X");
    }

    @Test
    void customPatternCanBeAdded() {
        // ユーザーが「【特別編】」のような独自接頭辞を切りたい場合
        var g = newGrouper(List.of("^【[^】]+】[\\s　]*"));
        assertThat(g.groupKey("【特別編】 番組Z")).isEqualTo("番組Z");
    }

    @Test
    void nullAndEmpty() {
        var g = newGrouper(DEFAULT_PATTERNS);
        assertThat(g.groupKey(null)).isEqualTo("");
        assertThat(g.groupKey("")).isEqualTo("");
    }
}
