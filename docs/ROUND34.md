# VEIN Round 34

Date: 2026-09-06

## 비로그인 공개 리포트 (MONETIZATION 단계2 ②)

**콘텐츠 우선 BM**의 첫 실체 — 누적 신호 성과를 "가공 통계"로만 공개하는
주간 패턴 성과 리포트를 백엔드에 구현했다. 개별 신호·종목·원시 캔들은
노출하지 않으므로 법적 안전지대(조언이 아닌 사실 서술)와 상업 논리
(원시 데이터를 다 열면 가입 이유가 없다)가 같은 방향을 가리킨다.

### 추가된 것

- **`com.vein.report` 패키지**
  - `WeeklyReportService` — `SignalPerformanceService.summary`를 읽어 트레일링
    창(기본 90일) 스냅샷을 조립. 전체 가중 집계(`overall`) + 패턴×시장×봉 행(`rows`)
    + 표본 5건 이상 최고/최저 하이라이트(`highlights`). JSON과 Markdown 두 표현.
    "weekly"는 데이터 창이 아니라 **발행 주기**다 — 적중률이 한 주 노이즈가 아니라
    누적 표본에서 나오게 한다.
  - `PublicReportController` — `GET /api/v1/public/reports/weekly`(JSON),
    `GET /api/v1/public/reports/weekly.md`(블로그·SNS 붙여넣기용 Markdown).
  - `WeeklyReportDto` — Report / Overall / PatternStat / Highlight.
- **`PublicRateLimitFilter`** — `/api/v1/public/**`에만 적용되는 IP당 60 req/min
  고정창 인메모리 레이트리밋. 초과 시 `429 RATE_LIMITED`(+`Retry-After: 60`).
  현재 API 전체에 레이트리밋이 없어(외부 provider용 Resilience4j만) 공개 경로의
  봇 스크래핑을 막기 위한 최소 방어. bucket4j 등 의존성 없이 구현.
- **`SecurityConfig`** — `PUBLIC_ENDPOINTS`에 `/api/v1/public/**` 추가(permitAll).
- **`ErrorCode.RATE_LIMITED`** — HTTP 429.

### 컴플라이언스 선

- 리포트 전문에 "투자 참고용 · 투자권유 아님 · 종목 추천이 아니라 과거 패턴의
  통계 요약 · 과거 성과가 미래 수익을 보장하지 않음" 고지를 `disclaimer`로 실어 반환.
- 공개하는 것은 `(type, market, timeframe)` 집계뿐 — SummaryRow 자체에 종목·신호
  식별자가 없어 구조적으로 개별 정보가 새지 않는다.

### 검증

- `gradlew compileJava` 성공(한글+공백 경로라 `gradlew test`는 @argfile 인코딩
  버그로 스킵 — 코드 문제 아님). 런타임 확인은 docker(PG/Redis)+백엔드 기동 후
  `GET /api/v1/public/reports/weekly` 호출로.

### 남은 것 (단계2 나머지)

- `POST /auth/signup`(가입 즉시 APPROVED, `vein.signup.auto-approve` 토글) + 가입 레이트리밋
- FE 비로그인 랜딩 페이지(실측 적중률 노출 + 가입 CTA + 리포트 링크)
- 유입 추적 컬럼(`users.signup_source/referrer`), 후원 링크
