# VEIN Round 176

Date: 2026-09-13

## Bearer 토큰 추출 분리 + 단위 테스트 (BE, 리팩터+회귀 보호)

자율 루프 R176. JWT 인증 필터의 Authorization 헤더 파싱이 `doFilterInternal`에
인라인되어 테스트 불가였다. Bearer 토큰 추출을 순수 함수로 **분리**(동작 보존)하고 테스트 추가.

### 바뀐 것

- `JwtAuthFilter.resolveBearerToken(header)` 순수 static 추출: 헤더 null·`"Bearer "`
  접두사(대소문자 구분) 아님 → null, 접두사 뒤 값 trim. 필터는 `token != null && 인증
  없음`으로 단순화 → **동작 무변경**(기존 header null/접두사 가드를 null 반환으로 매핑).
- 신규 `JwtAuthFilterTest` (+4):
  - "Bearer abc123" → "abc123", 앞뒤 공백 trim.
  - null·"Basic abc"·"bearer abc"(소문자) → null.
  - "Bearer "만 → 빈 문자열(null 아님, 이후 parse에서 거부).

### 검증

ASCII 경로 복사본 `gradlew test --tests JwtAuthFilterTest` **BUILD SUCCESSFUL**.
분리는 순수 추출이라 인증 흐름 동일.
