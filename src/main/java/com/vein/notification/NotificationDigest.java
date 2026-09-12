package com.vein.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vein.common.TimeUtil;
import com.vein.notification.NotificationDigestDto.CategoryCount;
import com.vein.notification.NotificationDigestDto.Digest;

/**
 * 알림 다이제스트 순수 집계기 (Track A #3). DB/컨텍스트 없이 알림 목록만으로 요약을 만든다
 * (그래서 단위 테스트가 쉬움). 분류는 스키마에 type 컬럼이 없으므로 구조 컬럼({@code signalId})
 * + 생성부의 <b>안정적인 영문 제목 마커</b>에서 유도한다:
 * <ul>
 *   <li>{@code signalId != null} → SIGNAL (알림규칙이 발생시킨 패턴 신호)</li>
 *   <li>제목 {@code "Scanner match:"} → SCANNER (조건검색 저장식 매칭)</li>
 *   <li>제목 {@code "Liquidation spike"} → LIQUIDATION (청산 급증)</li>
 *   <li>그 외 → SYSTEM</li>
 * </ul>
 */
public final class NotificationDigest {

    /** 안정적 출력을 위한 고정 분류 순서와 한글 라벨. */
    private static final List<String[]> CATEGORY_ORDER = List.of(
            new String[] {"SIGNAL", "패턴 신호"},
            new String[] {"SCANNER", "조건검색"},
            new String[] {"LIQUIDATION", "청산 급증"},
            new String[] {"SYSTEM", "시스템"});

    private static final int RECENT_LIMIT = 5;

    /**
     * 분류 유도용 제목 마커. 알림 생성부(ConditionScannerService·LiquidationService)와 여기 분류부가
     * 이 상수를 공유해 문자열 드리프트(한쪽만 바뀌면 분류 깨짐)를 막는다(R70).
     */
    public static final String SCANNER_MATCH_PREFIX = "Scanner match:";
    public static final String LIQUIDATION_SPIKE_PREFIX = "Liquidation spike";

    private NotificationDigest() {
    }

    /**
     * 다이제스트 입력 한 건(엔티티에서 사영). {@code read}는 이미 읽음 여부, {@code createdAt}는
     * 생성 시각. 목록은 <b>최신순(내림차순)</b>으로 넘어온다고 가정한다.
     */
    public record Entry(String id, Long signalId, String title, String body, String status,
                        boolean read, Instant createdAt, Instant heldUntil) {
    }

    /** 창({@code windowHours}) 안의 알림들을 분류·집계하고 요약 문장을 만든다. */
    public static Digest summarize(List<Entry> entries, int windowHours, Instant now) {
        List<Entry> rows = entries == null ? List.of() : entries;
        int total = rows.size();
        int unread = 0;
        int released = 0;

        // 분류별 (total, unread) 누적.
        Map<String, int[]> byCategory = new LinkedHashMap<>();
        List<NotificationDto> recent = new ArrayList<>();
        for (Entry e : rows) {
            if (e.heldUntil() != null) {
                released++;   // 조용한 시간에 보류됐다 창 안에 방출됨(findSince가 이미 held<=now만 통과)
            }
            if (!e.read()) {
                unread++;
                if (recent.size() < RECENT_LIMIT) {
                    recent.add(toDto(e));
                }
            }
            String category = category(e.signalId(), e.title());
            int[] c = byCategory.computeIfAbsent(category, k -> new int[2]);
            c[0]++;
            if (!e.read()) {
                c[1]++;
            }
        }

        List<CategoryCount> categories = new ArrayList<>();
        for (String[] cat : CATEGORY_ORDER) {
            int[] c = byCategory.get(cat[0]);
            if (c != null && c[0] > 0) {
                categories.add(new CategoryCount(cat[0], cat[1], c[0], c[1]));
            }
        }

        String summary = summary(windowHours, total, unread, categories);
        return new Digest(windowHours, TimeUtil.toIso(now), total, unread, released, categories, recent, summary);
    }

    /** signalId(구조) + 제목 마커로 분류. */
    static String category(Long signalId, String title) {
        if (signalId != null) {
            return "SIGNAL";
        }
        String t = title == null ? "" : title;
        if (t.startsWith(SCANNER_MATCH_PREFIX)) {
            return "SCANNER";
        }
        if (t.startsWith(LIQUIDATION_SPIKE_PREFIX)) {
            return "LIQUIDATION";
        }
        return "SYSTEM";
    }

    /** 창 길이를 사람이 읽는 라벨로: 24 초과 & 24의 배수면 "N일"(예: 168→7일), 그 외 "N시간". */
    private static String windowLabel(int windowHours) {
        return (windowHours > 24 && windowHours % 24 == 0)
                ? "최근 " + (windowHours / 24) + "일"
                : "최근 " + windowHours + "시간";
    }

    private static String summary(int windowHours, int total, int unread, List<CategoryCount> categories) {
        if (total == 0) {
            return windowLabel(windowHours) + " 새 알림이 없습니다.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(windowLabel(windowHours)).append(" 알림 ").append(total)
                .append("건 (안읽음 ").append(unread).append("건)");
        if (!categories.isEmpty()) {
            sb.append(" · ");
            for (int i = 0; i < categories.size(); i++) {
                if (i > 0) {
                    sb.append(" · ");
                }
                CategoryCount c = categories.get(i);
                sb.append(c.label()).append(' ').append(c.total());
            }
        }
        return sb.toString();
    }

    private static NotificationDto toDto(Entry e) {
        Long signalId = e.signalId();
        return new NotificationDto(
                e.id(),
                e.status(),
                e.title(),
                e.body(),
                signalId,
                TimeUtil.toIso(e.createdAt()),
                null);
    }
}
