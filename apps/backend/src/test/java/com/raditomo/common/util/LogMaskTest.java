package com.raditomo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskTest {

    @Test
    void email_masksLocalPartExceptFirstChar() {
        assertThat(LogMask.email("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(LogMask.email("keitan339.dev@gmail.com")).isEqualTo("k***@gmail.com");
    }

    @Test
    void email_singleCharLocal_fullyMasked() {
        assertThat(LogMask.email("a@example.com")).isEqualTo("***@example.com");
    }

    @Test
    void email_nullOrEmpty_returnsAsterisks() {
        assertThat(LogMask.email(null)).isEqualTo("***");
        assertThat(LogMask.email("")).isEqualTo("***");
    }

    @Test
    void email_invalid_noAtSign_fullyMasked() {
        // @ が無い場合はメールアドレスとして信頼せず安全側で全マスク
        assertThat(LogMask.email("noatsign")).isEqualTo("***");
        assertThat(LogMask.email("@startsWithAt.com")).isEqualTo("***");
    }
}
