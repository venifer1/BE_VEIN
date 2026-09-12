# VEIN Round 85

Date: 2026-09-12

## 신호 상세 유효기간(만료 D-day) 노출 (BE+FE)

자율 루프 R85. R82에서 만료 신호를 목록/추천에서 걸렀지만, **활성(DETECTED/NEAR_COMPLETION)
신호가 "언제까지 유효한지"**는 어디에도 안 보였다. setup이 며칠 남았는지는 진입 판단의 핵심
정보(오늘 볼지, 지나가도 되는지)인데 상세가 탐지 시각만 보여줬다.

### 바뀐 것

**백엔드**
- `SignalDetailDto`에 `expiresAt` 추가(`detectedAt` 뒤). `SignalService.detail()`이
  `TimeUtil.toIso(signal.getExpiresAt())`로 채움(null-safe). 생성자는 1곳뿐이라 파급 없음.

**프론트**
- `lib/types.ts` `SignalDetail`에 `expires_at?` 추가.
- 신호 상세 헤더 탐지 시각 아래 **유효기간 라인**: 활성 신호이고 `expires_at`이 있을 때
  `유효기간 {일시} (D-N)` 표시, 만료 임박(D-1 이하)은 경고색, 이미 지났으면 "유효기간 만료됨 ·
  갱신 대기"(destructive). 만료/무효 상태 신호엔 미표시.
- `lib/mockData.ts` `getSignalDetail`에 `expires_at`(탐지시각+30일, detected_at 기반이라 결정적)
  추가로 mock 패리티 유지.

### 검증

- BE: `gradlew test` 전체 그린(ASCII 경로), 재기동 후 실측 `GET /signals/88`(DETECTED) →
  `expires_at=2026-09-14`(약 1.4일 남음).
- FE: `tsc`·`next build` 통과. 재기동 후 신호 상세(Liberty Media US TOP)에 "유효기간 … (D-N)"
  렌더 확인(스크린샷). 라이브 Chrome full smoke **0에러**.
