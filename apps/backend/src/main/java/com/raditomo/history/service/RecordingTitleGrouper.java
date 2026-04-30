package com.raditomo.history.service;

import com.raditomo.history.RecordingProperties;
import com.raditomo.history.RecordingProperties.TitleGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 録音ライブラリのタイトル「グループキー」を導出する。
 *
 * 設定 {@code raditomo.recording.title-groups} に列挙された
 * {@code (pattern, groupKey)} を順に適用し、最初に {@code pattern}
 * （正規表現・部分一致）がタイトルにマッチした {@code groupKey} を返す。
 * どれにもマッチしなければタイトルそのもの（trim 済み）を返す。
 *
 * 例（設定 {@code "らじらー[！!][　 ]サンデー" → "らじらー！　サンデー"} の場合）:
 * - 「らじらー！　サンデー　８時台 ...」  → 「らじらー！　サンデー」
 * - 「らじらー!　サンデー　９時台 ...」    → 「らじらー！　サンデー」（半角！でも match）
 * - 「らじらー！ サンデー」                → 「らじらー！　サンデー」（半角空白でも match）
 * - 「アニメイトTV」                       → 「アニメイトTV」（マッチなし）
 */
@Component
@Slf4j
public class RecordingTitleGrouper {

    private final List<CompiledGroup> groups;

    public RecordingTitleGrouper(RecordingProperties props) {
        List<CompiledGroup> compiled = new ArrayList<>();
        for (TitleGroup g : props.titleGroups()) {
            try {
                compiled.add(new CompiledGroup(Pattern.compile(g.pattern()), g.groupKey()));
            } catch (PatternSyntaxException e) {
                log.error("Invalid title-group pattern: '{}' - skipping ({})",
                        g.pattern(), e.getMessage());
            }
        }
        this.groups = List.copyOf(compiled);
        log.info("RecordingTitleGrouper loaded {} group rule(s)", groups.size());
    }

    public String groupKey(String title) {
        if (title == null) return "";
        for (CompiledGroup g : groups) {
            if (g.pattern.matcher(title).find()) {
                return g.groupKey;
            }
        }
        return title.trim();
    }

    private record CompiledGroup(Pattern pattern, String groupKey) {}
}
