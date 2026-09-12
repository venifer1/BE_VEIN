# VEIN Round 87

Date: 2026-09-13

## API_CONTRACT.md 동기화 (R77~R86 델타 + 누락 엔드포인트) — 문서

자율 루프 R87. R74에서 계약서를 R73까지 동기화한 뒤 R77~R86에서 계약 표면이 여러 번
바뀌었는데 단일 진실 소스(`API_CONTRACT.md`)에 미반영이었다. 문서↔코드 정적 대조로
누락/드리프트를 찾아 메꿨다. 코드 변경 없음.

### 반영한 델타

- **누락 엔드포인트 2개 추가**(그동안 계약서에 아예 없었음 — FE가 사용 중):
  - `GET /signals/{id}/performance` — 이 신호의 실현 성과(`horizons[]{horizon,price,
    return_pct,mfe_pct,mae_pct,evaluated_at}`, 신선하면 빈 배열).
  - `GET /signals/performance/summary` — 패턴 유형별 과거 실측 집계(해자). 쿼리
    `type/market/timeframe/horizon/from/to/bucket=MONTH`, `rows[]{…sample_size,hit_rate,
    avg_return_pct,median_return_pct,avg_mfe_pct,avg_mae_pct}`. 스캐너 성과 스트립·**신호
    상세 base-rate(R86)**·Explain Confidence가 이걸 쓴다.
- **R77 무효화 완충** — `invalidation`을 `{rule, price, buffer_pct, effective_price}`로 명세.
  `effective_price = price × (1 − buffer_pct)`, 완충 없는 규칙은 두 필드 null. R:R은
  effective_price 우선.
- **R85 유효기간** — `GET /signals/{id}`에 `expires_at`(nullable) 추가 명시.
- **R79 피드백 무결성** — `POST /explain/{id}/feedback`의 `helpful`이 필수(Boolean),
  누락/null이면 400임을 명시.
- **R80 IMALOL C예상가** — `/signals` 카드 `c_target`이 ABC/TOP 전용이 아니라 IMALOL도
  포함(projectedClose=C예상가)임을 정정.
- **R49/R78 에러 정규화** — 4xx 정규화 규약 줄에 미매핑 경로 404(R49), 권한 부족 403(R78)
  추가(기존엔 R43 400/405만).

### 검증

- 문서↔코드 정적 대조: `SignalDetailDto.InvalidationDto{rule,price,bufferPct,effectivePrice}`,
  `SignalPerformanceDto.{Detail,HorizonResult,SummaryRow}`, `SignalDetailDto.expiresAt`,
  `ExplainFeedbackRequest.helpful(@NotNull)`, `GlobalExceptionHandler`(404/403 핸들러)와
  일치 확인. 코드 무변경이라 빌드/테스트 영향 없음.
</content>
