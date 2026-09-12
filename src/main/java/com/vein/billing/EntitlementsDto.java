package com.vein.billing;

import java.util.List;

/**
 * 구독 엔타이틀먼트 계약 (Track C 착수, R52). 티어(FREE/PRO)에서 파생한 기능/한도. 결제 연동은
 * 후속이라 여기서는 "무엇이 얼마나 열려 있는지"만 노출한다. {@code limit == -1}은 무제한.
 */
public final class EntitlementsDto {

    private EntitlementsDto() {
    }

    /** {@code GET /api/v1/me/entitlements} body. */
    public record Status(String tier, boolean pro, List<Feature> features) {
    }

    /** 기능별 한도. {@code limit}: 허용 개수, {@code -1}이면 무제한. */
    public record Feature(String key, String label, int limit) {
    }
}
