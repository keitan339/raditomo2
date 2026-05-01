package com.raditomo.common.util;

/**
 * ログ出力用 PII マスク。
 *
 * 設計参照: docs/design/06-docker-security.md "ログのPIIマスク"
 *   - メールアドレス等は INFO 以上では出さない or 部分マスク
 */
public final class LogMask {

    private LogMask() {}

    /**
     * メールアドレスを部分マスクする。
     *
     * <ul>
     *   <li>{@code "alice@example.com"} → {@code "a***@example.com"}</li>
     *   <li>{@code "ab@example.com"}    → {@code "a***@example.com"}</li>
     *   <li>{@code "a@example.com"}     → {@code "***@example.com"}</li>
     *   <li>{@code "noatsign"}          → {@code "***"}（@ なしは安全側で全マスク）</li>
     *   <li>{@code null} / 空           → {@code "***"}</li>
     * </ul>
     */
    public static String email(String email) {
        if (email == null || email.isEmpty()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) return "***" + domain;
        return local.charAt(0) + "***" + domain;
    }
}
