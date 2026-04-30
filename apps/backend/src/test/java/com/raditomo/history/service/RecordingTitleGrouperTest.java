package com.raditomo.history.service;

import com.raditomo.history.RecordingProperties;
import com.raditomo.history.RecordingProperties.TitleGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingTitleGrouperTest {

    /** application.yml のデフォルト設定と同じグループ。 */
    private static final List<TitleGroup> DEFAULT_GROUPS = List.of(
            new TitleGroup("らじらー[！!][　 ]サンデー", "らじらー！　サンデー"));

    private RecordingTitleGrouper newGrouper(List<TitleGroup> groups) {
        return new RecordingTitleGrouper(new RecordingProperties(groups));
    }

    @Test
    void radirerSundayGroupsAllHourBands() {
        var g = newGrouper(DEFAULT_GROUPS);
        assertThat(g.groupKey("らじらー！　サンデー　８時台　五百城茉央"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　９時台　五百城茉央　川端晃菜"))
                .isEqualTo("らじらー！　サンデー");
        assertThat(g.groupKey("らじらー！　サンデー　１０時台　五百城茉央　黒見明香"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void radirerSundayMatchesHalfwidthExclamationOrSpace() {
        var g = newGrouper(DEFAULT_GROUPS);
        // 半角!
        assertThat(g.groupKey("らじらー!　サンデー　８時台 出演者"))
                .isEqualTo("らじらー！　サンデー");
        // 半角空白
        assertThat(g.groupKey("らじらー！ サンデー　９時台"))
                .isEqualTo("らじらー！　サンデー");
        // 半角! + 半角空白
        assertThat(g.groupKey("らじらー! サンデー　１０時台"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void unrelatedTitleIsKeptAsIs() {
        var g = newGrouper(DEFAULT_GROUPS);
        assertThat(g.groupKey("オールナイトニッポン"))
                .isEqualTo("オールナイトニッポン");
        assertThat(g.groupKey("らじらー！　サタデー"))
                .isEqualTo("らじらー！　サタデー");
    }

    @Test
    void emptyGroupListReturnsTitleAsIs() {
        var g = newGrouper(List.of());
        assertThat(g.groupKey("番組  "))
                .isEqualTo("番組");
    }

    @Test
    void invalidRegexIsSkippedAndOthersStillApply() {
        var g = newGrouper(List.of(
                new TitleGroup("[invalid(", "skipped"),
                new TitleGroup("らじらー[！!][　 ]サンデー", "らじらー！　サンデー")));
        assertThat(g.groupKey("らじらー！　サンデー　１０時台"))
                .isEqualTo("らじらー！　サンデー");
    }

    @Test
    void nullAndEmpty() {
        var g = newGrouper(DEFAULT_GROUPS);
        assertThat(g.groupKey(null)).isEqualTo("");
        assertThat(g.groupKey("")).isEqualTo("");
    }
}
