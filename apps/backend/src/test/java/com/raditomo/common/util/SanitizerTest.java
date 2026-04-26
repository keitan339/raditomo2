package com.raditomo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizerTest {

    @Test
    void forFileName_replacesPathSeparatorsAndSpecialChars() {
        assertThat(Sanitizer.forFileName("a/b\\c:d*e?f\"g<h>i|j"))
                .isEqualTo("a_b_c_d_e_f_g_h_i_j");
    }

    @Test
    void forFileName_replacesNewlinesAndControlChars() {
        assertThat(Sanitizer.forFileName("foo\r\nbar\tbaz"))
                .isEqualTo("foo__bar_baz");
    }

    @Test
    void forFileName_keepsJapaneseAndSpaces() {
        assertThat(Sanitizer.forFileName("オールナイトニッポン GOLD"))
                .isEqualTo("オールナイトニッポン GOLD");
    }

    @Test
    void forFileName_returnsUnderscoreForEmpty() {
        assertThat(Sanitizer.forFileName(null)).isEqualTo("_");
        assertThat(Sanitizer.forFileName("")).isEqualTo("_");
    }
}
