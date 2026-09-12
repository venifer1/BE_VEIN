package com.vein.billing;

import java.util.List;

import com.vein.billing.EntitlementsDto.Feature;
import com.vein.billing.EntitlementsDto.Status;

/**
 * 티어 → 엔타이틀먼트 순수 매핑 (Track C, R52). DB 없이 테스트 가능. 한도는 여기 한 곳에서만
 * 정의해 서비스/게이트가 공유한다. {@code -1} = 무제한(PRO).
 */
public final class Entitlements {

    public static final String FREE = "FREE";
    public static final String PRO = "PRO";

    public static final String SAVED_SCANNER_RULES = "SAVED_SCANNER_RULES";
    public static final String ALERTS = "ALERTS";

    private static final int FREE_SCANNER_RULES = 3;
    private static final int FREE_ALERTS = 10;

    private Entitlements() {
    }

    /** 알 수 없는/누락 티어는 FREE로 정규화. */
    public static String normalize(String tier) {
        return PRO.equalsIgnoreCase(tier) ? PRO : FREE;
    }

    public static boolean isPro(String tier) {
        return PRO.equals(normalize(tier));
    }

    /** 저장 조건검색식 한도(-1=무제한). */
    public static int scannerRuleLimit(String tier) {
        return isPro(tier) ? -1 : FREE_SCANNER_RULES;
    }

    /** 신호 알림 규칙 한도(-1=무제한). */
    public static int alertLimit(String tier) {
        return isPro(tier) ? -1 : FREE_ALERTS;
    }

    /** 사용량 없이(0) 한도만. */
    public static Status forTier(String tier) {
        return forTier(tier, 0, 0);
    }

    /** 현재 사용량을 함께 반영한 엔타이틀먼트. */
    public static Status forTier(String tier, int scannerUsed, int alertUsed) {
        String t = normalize(tier);
        List<Feature> features = List.of(
                new Feature(SAVED_SCANNER_RULES, "저장 조건검색식", scannerRuleLimit(t), scannerUsed),
                new Feature(ALERTS, "신호 알림 규칙", alertLimit(t), alertUsed));
        return new Status(t, isPro(t), features);
    }
}
