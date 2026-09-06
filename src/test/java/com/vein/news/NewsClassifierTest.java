package com.vein.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class NewsClassifierTest {

    private static final Map<String, String> INDEX = Map.of(
            "btc", "KRW-BTC",
            "비트코인", "KRW-BTC",
            "bitcoin", "KRW-BTC",
            "eth", "KRW-ETH",
            "이더리움", "KRW-ETH",
            "ethereum", "KRW-ETH");

    @Test
    void classifiesSentimentByKeywordCounts() {
        assertThat(NewsClassifier.classify("비트코인 급등", null)).isEqualTo(NewsSentiment.POSITIVE);
        assertThat(NewsClassifier.classify("거래소 해킹 발생", null)).isEqualTo(NewsSentiment.NEGATIVE);
        assertThat(NewsClassifier.classify("Bitcoin to rally", null)).isEqualTo(NewsSentiment.POSITIVE);
        assertThat(NewsClassifier.classify("Exchange hack and lawsuit", null)).isEqualTo(NewsSentiment.NEGATIVE);
        assertThat(NewsClassifier.classify("시장 보합세 유지", null)).isEqualTo(NewsSentiment.NEUTRAL);
        assertThat(NewsClassifier.classify(null, null)).isEqualTo(NewsSentiment.NEUTRAL);
    }

    @Test
    void tagsInstrumentsFromKoEnNames() {
        assertThat(NewsClassifier.tag("비트코인 급등", null, INDEX)).containsExactly("KRW-BTC");
        assertThat(NewsClassifier.tag("Ethereum ETF approved", null, INDEX)).containsExactly("KRW-ETH");
        assertThat(NewsClassifier.tag("시장 잠잠", null, INDEX)).isEmpty();
    }

    @Test
    void asciiAliasRequiresWordBoundary() {
        // "ban" must not tag from "urban"; "btc" should still match standalone.
        assertThat(NewsClassifier.tag("urban development", null, INDEX)).isEmpty();
        assertThat(NewsClassifier.tag("BTC dominance rises", null, INDEX)).containsExactly("KRW-BTC");
    }
}
