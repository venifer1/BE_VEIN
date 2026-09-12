package com.vein.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.notification.NotificationDigest.Entry;
import com.vein.notification.NotificationDigestDto.CategoryCount;
import com.vein.notification.NotificationDigestDto.Digest;

/**
 * 순수 단위(DB/컨텍스트 불필요). 분류 유도, 집계, recent 표본, 요약 문장을 고정한다.
 */
class NotificationDigestTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    private static Entry entry(String id, Long signalId, String title, boolean read, int minAgo) {
        return new Entry(id, signalId, title, "body", read ? "READ" : "CREATED", read,
                NOW.minusSeconds(minAgo * 60L), null);
    }

    private static Entry heldEntry(String id, Long signalId, String title, boolean read, int minAgo) {
        return new Entry(id, signalId, title, "body", read ? "READ" : "CREATED", read,
                NOW.minusSeconds(minAgo * 60L), NOW.minusSeconds(minAgo * 30L));
    }

    @Test
    void categorizesBySignalIdAndTitleMarkers() {
        assertThat(NotificationDigest.category(5L, "BTC ABC signal")).isEqualTo("SIGNAL");
        assertThat(NotificationDigest.category(null, "Scanner match: RSI reboot")).isEqualTo("SCANNER");
        assertThat(NotificationDigest.category(null, "Liquidation spike HIGH")).isEqualTo("LIQUIDATION");
        assertThat(NotificationDigest.category(null, "Welcome")).isEqualTo("SYSTEM");
        assertThat(NotificationDigest.category(null, null)).isEqualTo("SYSTEM");
    }

    @Test
    void aggregatesCountsAndCategoriesInFixedOrder() {
        List<Entry> entries = List.of(
                entry("1", 10L, "BTC ABC signal", false, 5),
                entry("2", 11L, "ETH TOP signal", true, 10),
                entry("3", null, "Scanner match: vol surge", false, 15),
                entry("4", null, "Liquidation spike HIGH", false, 20),
                entry("5", null, "Welcome aboard", true, 25));

        Digest d = NotificationDigest.summarize(entries, 24, NOW);

        assertThat(d.total()).isEqualTo(5);
        assertThat(d.unread()).isEqualTo(3);
        assertThat(d.released()).isZero();
        assertThat(d.windowHours()).isEqualTo(24);
        // 고정 우선순위: SIGNAL, SCANNER, LIQUIDATION, SYSTEM
        assertThat(d.categories()).extracting(CategoryCount::category)
                .containsExactly("SIGNAL", "SCANNER", "LIQUIDATION", "SYSTEM");
        CategoryCount signal = d.categories().get(0);
        assertThat(signal.total()).isEqualTo(2);
        assertThat(signal.unread()).isEqualTo(1);
        assertThat(signal.label()).isEqualTo("패턴 신호");
    }

    @Test
    void recentHoldsNewestUnreadUpToFive() {
        List<Entry> entries = List.of(
                entry("a", 1L, "s1 signal", false, 1),
                entry("b", 2L, "s2 signal", true, 2),  // read → excluded
                entry("c", 3L, "s3 signal", false, 3),
                entry("d", 4L, "s4 signal", false, 4),
                entry("e", 5L, "s5 signal", false, 5),
                entry("f", 6L, "s6 signal", false, 6),
                entry("g", 7L, "s7 signal", false, 7)); // 6th unread → trimmed

        Digest d = NotificationDigest.summarize(entries, 24, NOW);

        assertThat(d.recent()).hasSize(5);
        assertThat(d.recent()).extracting(NotificationDto::id)
                .containsExactly("a", "c", "d", "e", "f");
        assertThat(d.recent().get(0).readAt()).isNull();
    }

    @Test
    void releasedCountsHeldNotificationsThatBecameVisible() {
        // 2 held(방출됨) + 1 일반 → released=2
        List<Entry> entries = List.of(
                heldEntry("1", 10L, "s1 signal", false, 5),
                heldEntry("2", null, "Scanner match: x", true, 10),
                entry("3", 11L, "s3 signal", false, 15));

        Digest d = NotificationDigest.summarize(entries, 24, NOW);

        assertThat(d.total()).isEqualTo(3);
        assertThat(d.released()).isEqualTo(2);
    }

    @Test
    void emptyWindowGivesNoNewNotificationsSummary() {
        Digest d = NotificationDigest.summarize(List.of(), 12, NOW);

        assertThat(d.total()).isZero();
        assertThat(d.unread()).isZero();
        assertThat(d.categories()).isEmpty();
        assertThat(d.recent()).isEmpty();
        assertThat(d.summary()).isEqualTo("최근 12시간 새 알림이 없습니다.");
    }

    @Test
    void summaryListsWindowCountsAndCategories() {
        List<Entry> entries = List.of(
                entry("1", 10L, "BTC ABC signal", false, 5),
                entry("2", null, "Scanner match: vol surge", false, 15));

        Digest d = NotificationDigest.summarize(entries, 24, NOW);

        assertThat(d.summary()).isEqualTo("최근 24시간 알림 2건 (안읽음 2건) · 패턴 신호 1 · 조건검색 1");
    }
}
