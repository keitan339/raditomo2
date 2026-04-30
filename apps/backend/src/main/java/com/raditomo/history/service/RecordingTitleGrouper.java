package com.raditomo.history.service;

import com.raditomo.history.RecordingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 録音ライブラリでのタイトル「グループ化キー」を導出する。
 *
 * 設定 {@code raditomo.recording.title-group-patterns} に列挙された正規表現を、
 * タイトル末尾から順に {@code replaceFirst} で削除した結果（前後空白 trim）を返す。
 *
 * 例（デフォルトパターン適用時）:
 * - 「らじらー！　サンデー　８時台 山田・佐藤」  → 「らじらー！　サンデー」
 * - 「パンサー向井の#ふらっと (1)」              → 「パンサー向井の#ふらっと」
 * - 「○○ Part 2」                               → 「○○」
 * - 「○○ 第3回」                                → 「○○」
 * - 「アルファ」                                  → 「アルファ」
 *
 * 全角数字「１０時台」「（２）」も含めるかどうかはパターン側の責務（デフォルトでは
 * {@code [0-9０-９]} で半角・全角どちらにもマッチさせている）。
 */
@Component
@Slf4j
public class RecordingTitleGrouper {

    private final List<Pattern> patterns;

    public RecordingTitleGrouper(RecordingProperties props) {
        List<Pattern> compiled = new ArrayList<>();
        for (String regex : props.titleGroupPatterns()) {
            try {
                compiled.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                log.error("Invalid title-group-pattern: '{}' - skipping ({})", regex, e.getMessage());
            }
        }
        this.patterns = List.copyOf(compiled);
        log.info("RecordingTitleGrouper loaded {} pattern(s)", patterns.size());
    }

    public String groupKey(String title) {
        if (title == null) return "";
        String t = title;
        for (Pattern p : patterns) {
            t = p.matcher(t).replaceFirst("");
        }
        // 末尾の trim（半角/全角空白）
        return t.replaceAll("[\\s　]+$", "").trim();
    }
}
