package com.vein.onboarding;

import java.util.List;

import com.vein.onboarding.OnboardingDto.Status;
import com.vein.onboarding.OnboardingDto.Step;

/**
 * 온보딩 스텝 순수 조립기 (Track B #2). 각 스텝의 완료 여부(불리언)만 받아 고정 라벨·딥링크·
 * 진행률로 조립한다. DB/컨텍스트 없이 테스트 가능하도록 서비스에서 분리했다.
 */
public final class OnboardingSteps {

    private OnboardingSteps() {
    }

    /**
     * 실제 상태 4종 + 닫힘 여부로 체크리스트를 만든다. 스텝 순서·라벨·딥링크는 여기서 고정.
     */
    public static Status build(boolean hasWatchlistItem, boolean hasAlert, boolean hasPaperAccount,
                               boolean hasScannerRule, boolean dismissed) {
        List<Step> steps = List.of(
                new Step("WATCHLIST", "관심종목 추가", hasWatchlistItem, "/"),
                new Step("ALERT", "신호 알림 만들기", hasAlert, "/scanner"),
                new Step("PAPER", "모의투자 시작", hasPaperAccount, "/paper"),
                new Step("SCANNER", "조건검색식 저장", hasScannerRule, "/scanner"));
        int completed = (int) steps.stream().filter(Step::done).count();
        int total = steps.size();
        return new Status(steps, completed, total, completed == total, dismissed);
    }
}
