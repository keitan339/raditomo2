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
 *     title-groups:
 *       - pattern: "らじらー[！!][　 ]サンデー"
 *         group-key: "らじらー！　サンデー"
 * </pre>
 *
 * 番組タイトルが {@code pattern}（正規表現）にマッチすればライブラリ上は {@code groupKey} に集約する。
 * マッチしない番組はタイトルのまま（個別グループ）として扱う。
 */
@ConfigurationProperties(prefix = "raditomo.recording")
public record RecordingProperties(List<TitleGroup> titleGroups) {
    public RecordingProperties {
        titleGroups = titleGroups == null ? List.of() : List.copyOf(titleGroups);
    }

    /**
     * @param pattern  タイトルにマッチさせる正規表現（部分一致 OK）
     * @param groupKey マッチしたタイトルをまとめる canonical なグループ名
     */
    public record TitleGroup(String pattern, String groupKey) {}
}
