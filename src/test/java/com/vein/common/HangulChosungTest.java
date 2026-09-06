package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HangulChosungTest {

    @Test
    void extractsLeadingConsonants() {
        assertThat(HangulChosung.toChosung("삼성전자")).isEqualTo("ㅅㅅㅈㅈ");
        assertThat(HangulChosung.toChosung("카카오")).isEqualTo("ㅋㅋㅇ");
    }

    @Test
    void recognizesChosungQuery() {
        assertThat(HangulChosung.isChosungQuery("ㅅㅅ")).isTrue();
        assertThat(HangulChosung.isChosungQuery("삼성")).isFalse();
        assertThat(HangulChosung.isChosungQuery("AAPL")).isFalse();
        assertThat(HangulChosung.isChosungQuery("")).isFalse();
    }

    @Test
    void matchesByInitialConsonants() {
        assertThat(HangulChosung.matches("삼성전자", "ㅅㅅ")).isTrue();
        assertThat(HangulChosung.matches("삼성전자", "ㅅㅅㅈㅈ")).isTrue();
        assertThat(HangulChosung.matches("삼성전자", "ㅋㅋ")).isFalse();
        assertThat(HangulChosung.matches("카카오게임즈", "ㅋㅋ")).isTrue();
    }
}
