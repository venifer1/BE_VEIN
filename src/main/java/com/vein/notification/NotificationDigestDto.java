package com.vein.notification;

import java.util.List;

/**
 * 알림 다이제스트(요약) 계약 (Track A #3, 알림 노이즈 완화). R41 "오늘의 주목 신호"가 신호
 * 과다를, R42 "조용한 시간"이 알림 타이밍을 다뤘다면, 다이제스트는 <b>읽기 시점 요약</b>으로
 * "자리를 비운 사이 뭐가 왔나"를 한눈에 준다. 저장 데이터를 바꾸지 않는 순수 집계다.
 */
public final class NotificationDigestDto {

    private NotificationDigestDto() {
    }

    /**
     * {@code GET /api/v1/notifications/digest} body. {@code windowHours} 창 안에서 집계.
     * {@code categories}는 건수>0인 분류만, 고정 우선순위 순서. {@code recent}는 안읽은 최신
     * 표본(최대 5). {@code summary}는 사람이 읽는 한 줄.
     */
    public record Digest(int windowHours, String generatedAt, int total, int unread,
                         List<CategoryCount> categories, List<NotificationDto> recent, String summary) {
    }

    /**
     * 분류별 건수. {@code category} ∈ SIGNAL|SCANNER|LIQUIDATION|SYSTEM, {@code label}은 한글 표시.
     */
    public record CategoryCount(String category, String label, int total, int unread) {
    }
}
