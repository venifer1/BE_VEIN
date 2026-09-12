package com.vein.onboarding;

import java.util.List;

/**
 * 온보딩 "시작하기" 체크리스트 계약 (Track B #2). 신규 사용자가 핵심 기능에 도달하도록 홈에서
 * 안내한다. 스텝 완료 여부는 각 기능의 <b>실제 데이터에서 파생</b>(관심종목 항목·알림 규칙·
 * 모의투자 계좌·조건검색식 존재)하므로 별도로 저장하지 않는다. 사용자가 닫으면 다시 안 뜬다.
 */
public final class OnboardingDto {

    private OnboardingDto() {
    }

    /**
     * {@code GET /api/v1/me/onboarding} body. {@code allDone}이거나 {@code dismissed}면 FE는 카드를
     * 감춘다. {@code completed}/{@code total}로 진행률 표시.
     */
    public record Status(List<Step> steps, int completed, int total, boolean allDone, boolean dismissed) {
    }

    /** 체크리스트 한 항목. {@code href}는 해당 기능으로의 딥링크. */
    public record Step(String key, String label, boolean done, String href) {
    }
}
