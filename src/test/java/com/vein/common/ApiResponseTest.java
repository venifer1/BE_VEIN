package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 공통 성공 응답 봉투(부록 C-1) 빌더 회귀 보호(R172). data/meta 슬롯(freshness·nextCursor·
 * unreadCount)이 올바른 자리에 채워지는지 확인. traceId는 필터 미실행 시 null.
 */
class ApiResponseTest {

    @Test
    void of_dataOnly_leavesOptionalMetaNull() {
        ApiResponse<String> r = ApiResponse.of("x");
        assertThat(r.data()).isEqualTo("x");
        assertThat(r.meta().freshness()).isNull();
        assertThat(r.meta().nextCursor()).isNull();
        assertThat(r.meta().unreadCount()).isNull();
    }

    @Test
    void of_withFreshness_setsOnlyFreshness() {
        ApiResponse<String> r = ApiResponse.of("x", "FRESH");
        assertThat(r.meta().freshness()).isEqualTo("FRESH");
        assertThat(r.meta().nextCursor()).isNull();
    }

    @Test
    void of_withExplicitMeta_passesThrough() {
        ApiResponse.Meta meta = new ApiResponse.Meta("t1", "DELAYED", "cur", 3);
        ApiResponse<String> r = ApiResponse.of("x", meta);
        assertThat(r.meta()).isSameAs(meta);
    }

    @Test
    void list_setsNextCursorAndOptionalUnread() {
        ApiResponse<List<String>> a = ApiResponse.list(List.of("a", "b"), "cursor123");
        assertThat(a.data()).containsExactly("a", "b");
        assertThat(a.meta().nextCursor()).isEqualTo("cursor123");
        assertThat(a.meta().unreadCount()).isNull();

        ApiResponse<List<String>> b = ApiResponse.list(List.of("a"), "c2", 7);
        assertThat(b.meta().nextCursor()).isEqualTo("c2");
        assertThat(b.meta().unreadCount()).isEqualTo(7);
    }
}
