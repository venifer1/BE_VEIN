# VEIN Round 61

Date: 2026-09-12

## 스캐너 "관심종목만" 필터 노출 (FE)

자율 루프 R61. 백엔드는 `GET /signals?watchlist_only=`를 지원하는데(관심종목에 담긴 종목의
신호만) **스캐너 필터바에 노출이 없었다**. 내 관심종목 신호만 빠르게 보는 유용한 뷰라 토글로 연다.
R54(active_only)와 동일하게 **FE 전용**(기존 API 활용, 백엔드 무변경).

### 바뀐 것 (FE)

- `SignalFilterBar`에 **"관심종목만" 토글** 추가(`filter.watchlist_only`).
- 스캐너 URL 파라미터 `watch=true`로 상태 유지(`filterFromSearchParams`/`setFilterSearchParams`,
  `FILTER_QUERY_KEYS`). `hasFilter`에 watchlist_only 포함(빈 결과 시 초기화 안내 정확).
- mock `/signals`는 이미 watchlist_only를 처리(무변경).

### 검증

- 프론트 `typecheck`/`build` 통과.
- **라이브**: `GET /signals?watchlist_only=true&active_only=true` → 0(데모 관심종목 비어 있음),
  일반 → 20. **Chrome smoke 0에러**. 백엔드/계약 무변경.
