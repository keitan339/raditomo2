package com.raditomo.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 録音まわりの設定。
 *
 * application.yml 例:
 * <pre>
 * raditomo:
 *   recording:
 *     title-group-patterns:
 *       - "[\\s　]*[0-9０-９]{1,2}時台.*$"
 *       - "[\\s　]+Part\\s*[0-9０-９]+\\s*$"
 *       - "[\\s　]*[\\(（][0-9０-９]+[\\)）]\\s*$"
 *       - "[\\s　]*第[0-9０-９]+回\\s*$"
 * </pre>
 *
 * @param titleGroupPatterns 番組タイトルからグループキーを導出する際に
 *                           末尾から取り除くパターンの正規表現リスト。
 *                           各エントリは末尾アンカー ($) を含めること。順に適用される。
 */
@ConfigurationProperties(prefix = "raditomo.recording")
public record RecordingProperties(List<String> titleGroupPatterns) {
    public RecordingProperties {
        titleGroupPatterns = titleGroupPatterns == null ? List.of() : List.copyOf(titleGroupPatterns);
    }
}
