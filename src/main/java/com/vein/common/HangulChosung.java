package com.vein.common;

/**
 * Korean initial-consonant (초성) search helper. Extracts the leading jamo of
 * each Hangul syllable so a query like {@code "ㅅㅈ"} matches {@code "삼성전자"}.
 * Non-Hangul characters pass through unchanged (lower-cased) so mixed queries
 * (e.g. ASCII tickers) still work.
 */
public final class HangulChosung {

    private static final char HANGUL_BASE = 0xAC00;   // '가'
    private static final char HANGUL_LAST = 0xD7A3;   // '힣'
    private static final int JUNG_JONG = 21 * 28;     // medials * finals per initial

    /** 19 modern leading consonants (초성). */
    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private HangulChosung() {
    }

    /** True when {@code c} is a standalone leading-consonant jamo (ㄱ~ㅎ). */
    public static boolean isChosungJamo(char c) {
        for (char k : CHOSUNG) {
            if (k == c) {
                return true;
            }
        }
        return false;
    }

    /** True when the query is non-empty and consists solely of 초성 jamo. */
    public static boolean isChosungQuery(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        for (int i = 0; i < q.length(); i++) {
            if (!isChosungJamo(q.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reduce a string to its 초성 sequence. Each Hangul syllable becomes its
     * leading consonant; standalone jamo are kept; everything else is dropped
     * to keep matching tight (so ASCII queries should use plain substring).
     */
    public static String toChosung(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= HANGUL_BASE && c <= HANGUL_LAST) {
                int idx = (c - HANGUL_BASE) / JUNG_JONG;
                sb.append(CHOSUNG[idx]);
            } else if (isChosungJamo(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** True when {@code name}'s 초성 sequence contains the 초성 query. */
    public static boolean matches(String name, String chosungQuery) {
        if (name == null || chosungQuery == null || chosungQuery.isEmpty()) {
            return false;
        }
        return toChosung(name).contains(chosungQuery);
    }
}
