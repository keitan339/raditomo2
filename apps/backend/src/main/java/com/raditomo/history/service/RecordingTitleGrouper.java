package com.raditomo.history.service;

import java.util.regex.Pattern;

/**
 * 録音ライブラリでのタイトル「グループ化キー」を導出する。
 *
 * 番組名に出てくる以下の「枠」表記を取り除いた残りをグループキーとする:
 * - 「○時台 ...」（例: 「らじらー！　サンデー　8時台 ゲスト：...」）
 * - 「Part N」（半角・全角空白）
 * - 「(N)」「（N）」（半角・全角括弧）
 * - 「第N回」
 *
 * 元タイトルにこれらの表記が含まれない場合はトリムだけ返す（= 何もまとめない）。
 *
 * 例:
 * - 「らじらー！　サンデー　8時台 山田・佐藤」  → 「らじらー！　サンデー」
 * - 「らじらー！　サンデー　9時台 田中」        → 「らじらー！　サンデー」
 * - 「パンサー向井の#ふらっと (1)」              → 「パンサー向井の#ふらっと」
 * - 「○○ Part 2」                               → 「○○」
 * - 「○○ 第3回」                                → 「○○」
 * - 「アルファ」                                  → 「アルファ」
 */
public final class RecordingTitleGrouper {

    /** 半角・全角数字を許容するクラス。 */
    private static final String DIGIT = "[0-9０-９]";
    /** 末尾の「[空白]N時台 ...」を除去（出演者名などが続いてもまとめて落とす）。 */
    private static final Pattern HOUR_BAND =
            Pattern.compile("[\\s　]*" + DIGIT + "{1,2}時台.*$");
    /** 末尾の「[空白]Part N」。 */
    private static final Pattern PART_NUM =
            Pattern.compile("[\\s　]+Part\\s*" + DIGIT + "+\\s*$", Pattern.CASE_INSENSITIVE);
    /** 末尾の「(N)」「（N）」。 */
    private static final Pattern PAREN_NUM =
            Pattern.compile("[\\s　]*[\\(（]" + DIGIT + "+[\\)）]\\s*$");
    /** 末尾の「第N回」。 */
    private static final Pattern KAI =
            Pattern.compile("[\\s　]*第" + DIGIT + "+回\\s*$");

    private RecordingTitleGrouper() {}

    public static String groupKey(String title) {
        if (title == null) return "";
        String t = title;
        t = HOUR_BAND.matcher(t).replaceFirst("");
        t = PART_NUM.matcher(t).replaceFirst("");
        t = PAREN_NUM.matcher(t).replaceFirst("");
        t = KAI.matcher(t).replaceFirst("");
        // 末尾の trim（半角/全角空白）
        return t.replaceAll("[\\s　]+$", "").trim();
    }
}
