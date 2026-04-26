package com.raditomo.common.util;

/**
 * ファイルパスに含めて安全な文字列に変換する。
 *
 * 設計（要件 F2 / docs/design/05-batch-file-notification.md）に従い、以下を `_` に置換:
 * - `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, 改行（\r\n）、その他の制御文字
 */
public final class Sanitizer {

    private Sanitizer() {}

    public static String forFileName(String input) {
        if (input == null || input.isEmpty()) return "_";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|'
                    || c == '\r' || c == '\n' || Character.isISOControl(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
