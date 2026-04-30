package com.raditomo.history.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingTitleGrouperTest {

    @Test
    void hourBandIsStripped() {
        assertThat(RecordingTitleGrouper.groupKey("らじらー！　サンデー　8時台 山田・佐藤"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(RecordingTitleGrouper.groupKey("らじらー！　サンデー　9時台 田中"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(RecordingTitleGrouper.groupKey("らじらー！　サンデー　10時台"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void hourBandWithFullwidthDigits() {
        // 実 radiko データは全角数字「１０時台」のケースが多い
        assertThat(RecordingTitleGrouper.groupKey(
                "らじらー！　サンデー　１０時台　五百城茉央　黒見明香（乃木坂４６）"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(RecordingTitleGrouper.groupKey(
                "らじらー！　サンデー　９時台　五百城茉央"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(RecordingTitleGrouper.groupKey(
                "らじらー！　サンデー　８時台　五百城茉央"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void parenthesizedPartNumIsStripped() {
        assertThat(RecordingTitleGrouper.groupKey("パンサー向井の#ふらっと (1)"))
                .isEqualTo("パンサー向井の#ふらっと");
        assertThat(RecordingTitleGrouper.groupKey("番組A（2）"))
                .isEqualTo("番組A");
    }

    @Test
    void partNumIsStripped() {
        assertThat(RecordingTitleGrouper.groupKey("番組B Part 2"))
                .isEqualTo("番組B");
        assertThat(RecordingTitleGrouper.groupKey("番組B　Part3"))
                .isEqualTo("番組B");
    }

    @Test
    void kaiSuffixIsStripped() {
        assertThat(RecordingTitleGrouper.groupKey("番組C 第3回"))
                .isEqualTo("番組C");
    }

    @Test
    void titleWithoutSuffixIsUnchanged() {
        assertThat(RecordingTitleGrouper.groupKey("オールナイトニッポン"))
                .isEqualTo("オールナイトニッポン");
    }

    @Test
    void nullAndEmpty() {
        assertThat(RecordingTitleGrouper.groupKey(null)).isEqualTo("");
        assertThat(RecordingTitleGrouper.groupKey("")).isEqualTo("");
    }
}
