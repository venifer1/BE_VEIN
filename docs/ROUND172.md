# VEIN Round 172

Date: 2026-09-13

## 공통 성공 응답 봉투(ApiResponse) 빌더 단위 테스트 (BE, 회귀 보호)

자율 루프 R172. 모든 성공 응답이 통과하는 공통 봉투(부록 C-1) `ApiResponse.of`/`list`
빌더는 data/meta 슬롯(freshness·nextCursor·unreadCount)을 정확한 자리에 채워야 하는데
계약 테스트가 없었다.

### 바뀐 것

- 신규 `ApiResponseTest` (+4, 코드 변경 0 — public static 빌더 그대로 사용):
  - `of(data)`: 선택 meta(freshness·nextCursor·unreadCount) 전부 null.
  - `of(data, freshness)`: freshness만 설정, nextCursor null.
  - `of(data, meta)`: 명시 meta 그대로 통과.
  - `list(data, cursor)` / `list(data, cursor, unread)`: nextCursor·unreadCount 자리 확인.

### 검증

ASCII 경로 복사본 `gradlew test --tests ApiResponseTest` **BUILD SUCCESSFUL**. 코드 무변경.
traceId는 필터 미실행 컨텍스트라 단언 대상에서 제외.
