# VEIN Round 175

Date: 2026-09-13

## 공개 엔드포인트 레이트리밋 라우팅(PublicRateLimitFilter.ruleFor) 단위 테스트 (BE, 회귀 보호)

자율 루프 R175. 비로그인 공개 API 남용 방지의 핵심인 레이트리밋 규칙 선택
`ruleFor(uri)`(signup 정확매칭 시간당 10, `/public/` 접두사 분당 60, 그 외 미적용)는
보안 설정인데 테스트가 없었다.

### 바뀐 것

- `Rule` 레코드·`ruleFor`: `private` → package-private(동작 무변경, 테스트 접근용).
- 신규 `PublicRateLimitFilterTest` (+4):
  - signup 정확매칭·시간당 10.
  - **정확매칭이라 `/signup` 뒤 문자 붙으면 미매칭**(접두사 규칙에도 안 걸림).
  - `/public/` 접두사는 하위 경로까지 매칭·분당 60.
  - 비대상 경로·null → null(레이트리밋 미적용).

### 검증

ASCII 경로 복사본 `gradlew test --tests PublicRateLimitFilterTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
