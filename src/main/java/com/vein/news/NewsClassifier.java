package com.vein.news;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, dependency-free keyword/rule classifier for news items. No LLM, no I/O.
 *
 * <p>{@link #classify} derives a {@link NewsSentiment} by counting positive vs.
 * negative KO/EN keyword hits in the title+body. {@link #tag} returns the
 * instrument symbols whose base/Korean/English name appears in the text.
 *
 * <p>All matching is case-insensitive. Never throws on null input.
 */
public final class NewsClassifier {

    private NewsClassifier() {
    }

    /** Substrings that signal bullish/positive news (KO + EN, lowercased). */
    static final List<String> POSITIVE = List.of(
            // Korean
            "급등", "상승", "돌파", "신고가", "최고가", "승인", "호재", "강세", "반등",
            "상한가", "상장", "채택", "수혜", "흑자", "성장", "회복", "낙관", "매수",
            "급증", "폭등", "훈풍",
            // English
            "surge", "soar", "rally", "approve", "approval", "bullish", "gain",
            "jump", "spike", "record high", "all-time high", "breakout", "adopt",
            "upgrade", "outperform", "boost", "optimis");

    /** Substrings that signal bearish/negative news (KO + EN, lowercased). */
    static final List<String> NEGATIVE = List.of(
            // Korean
            "급락", "하락", "해킹", "규제", "매도", "폭락", "약세", "하한가", "상장폐지",
            "상폐", "소송", "고소", "고발", "조사", "압류", "동결", "적자", "파산",
            "디폴트", "청산", "급감", "위기", "악재", "제재", "금지", "사기", "유출",
            // English
            "crash", "plunge", "hack", "ban", "lawsuit", "bearish", "dump",
            "slump", "tumble", "selloff", "sell-off", "fraud", "exploit",
            "downgrade", "default", "bankrupt", "liquidat", "sanction",
            "probe", "lawsuit", "fear");

    /**
     * Built-in Korean and extra English aliases for major coins, keyed by symbol
     * base. The DB only stores English {@code name}, so this supplies KO names and
     * common variants. Values are matched case-insensitively as whole-ish tokens.
     */
    static final Map<String, List<String>> EXTRA_NAMES = Map.ofEntries(
            Map.entry("BTC", List.of("비트코인", "비트", "bitcoin")),
            Map.entry("ETH", List.of("이더리움", "이더", "ethereum", "ether")),
            Map.entry("XRP", List.of("리플", "ripple")),
            Map.entry("SOL", List.of("솔라나", "solana")),
            Map.entry("DOGE", List.of("도지", "도지코인", "dogecoin")),
            Map.entry("ADA", List.of("에이다", "카르다노", "cardano")),
            Map.entry("TRX", List.of("트론", "tron")),
            Map.entry("AVAX", List.of("아발란체", "avalanche")),
            Map.entry("LINK", List.of("체인링크", "chainlink")),
            Map.entry("DOT", List.of("폴카닷", "polkadot")),
            Map.entry("MATIC", List.of("폴리곤", "polygon")),
            Map.entry("BCH", List.of("비트코인캐시", "bitcoin cash")),
            Map.entry("LTC", List.of("라이트코인", "litecoin")),
            Map.entry("ETC", List.of("이더리움클래식", "ethereum classic")),
            Map.entry("ATOM", List.of("코스모스", "cosmos")),
            Map.entry("NEAR", List.of("니어", "near protocol")),
            Map.entry("SUI", List.of("수이", "sui")),
            Map.entry("SHIB", List.of("시바이누", "시바", "shiba inu")),
            Map.entry("APT", List.of("앱토스", "aptos")),
            Map.entry("SAND", List.of("샌드박스", "the sandbox", "sandbox")));

    private static final int MAX_TAGS = 5;

    /** Count pos/neg hits in title+body; ties and zero-hits resolve to NEUTRAL. */
    public static NewsSentiment classify(String title, String body) {
        String text = lower(title, body);
        if (text.isEmpty()) {
            return NewsSentiment.NEUTRAL;
        }
        int pos = countHits(text, POSITIVE);
        int neg = countHits(text, NEGATIVE);
        if (pos > neg) {
            return NewsSentiment.POSITIVE;
        }
        if (neg > pos) {
            return NewsSentiment.NEGATIVE;
        }
        return NewsSentiment.NEUTRAL;
    }

    /**
     * Symbols whose alias appears in the text. {@code aliasToSymbol} maps a
     * lowercased alias token (coin base, KO name, EN name) to its instrument
     * symbol (e.g. {@code "비트코인" -> "KRW-BTC"}). Capped at {@value #MAX_TAGS},
     * de-duplicated, order-stable by first appearance in the index.
     */
    public static List<String> tag(String title, String body, Map<String, String> aliasToSymbol) {
        if (aliasToSymbol == null || aliasToSymbol.isEmpty()) {
            return List.of();
        }
        String text = lower(title, body);
        if (text.isEmpty()) {
            return List.of();
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : aliasToSymbol.entrySet()) {
            String alias = e.getKey();
            if (alias == null || alias.length() < 2) {
                continue; // avoid noise from 1-char tokens
            }
            // ASCII ticker aliases (e.g. IN, SOON, GAS) collide with ordinary English
            // prose. Require length >= 3 and exclude common English words; Korean
            // names (non-ASCII) keep the >= 2 rule.
            if (isAsciiWord(alias) && (alias.length() < 3 || STOP_WORDS.contains(alias))) {
                continue;
            }
            if (containsToken(text, alias)) {
                symbols.add(e.getValue());
                if (symbols.size() >= MAX_TAGS) {
                    break;
                }
            }
        }
        return new ArrayList<>(symbols);
    }

    /** Common English words that are also Upbit tickers — never auto-tag by these. */
    static final Set<String> STOP_WORDS = Set.of(
            "soon", "gas", "sun", "win", "any", "era", "alt", "new", "now", "one",
            "two", "top", "buy", "sell", "form", "fork", "ai", "all", "for", "the",
            "and", "out", "use", "data", "move", "near", "open", "real", "well",
            "fact", "time", "id", "us", "go", "me", "on", "in", "by");

    private static int countHits(String text, List<String> keywords) {
        int n = 0;
        for (String kw : keywords) {
            int from = 0;
            int idx;
            while ((idx = text.indexOf(kw, from)) >= 0) {
                n++;
                from = idx + kw.length();
            }
        }
        return n;
    }

    /**
     * True if {@code alias} occurs in {@code text}. For aliases that are purely
     * ASCII-letter words (e.g. "btc", "ban") we require word-ish boundaries so
     * "ban" doesn't match inside "urban"; CJK aliases match by plain substring
     * (no whitespace word boundaries in Korean).
     */
    private static boolean containsToken(String text, String alias) {
        if (isAsciiWord(alias)) {
            int from = 0;
            int idx;
            while ((idx = text.indexOf(alias, from)) >= 0) {
                boolean leftOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
                int end = idx + alias.length();
                boolean rightOk = end >= text.length() || !isWordChar(text.charAt(end));
                if (leftOk && rightOk) {
                    return true;
                }
                from = idx + 1;
            }
            return false;
        }
        return text.contains(alias);
    }

    private static boolean isAsciiWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean letterOrDigitOrSpace = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '-';
            if (!letterOrDigitOrSpace) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static String lower(String title, String body) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (body != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(body);
        }
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
