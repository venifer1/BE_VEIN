# VEIN Round 84

Date: 2026-09-12

## 신호 상세 "다음 액션" 시장별 현실화 — 주식은 현물 매수만 (FE)

자율 루프 R84. 신호 상세의 "다음 액션" 패널(R31, 신호→모의 진입 다리)이 **모든 신호에 대해
`investment_type:"FUTURES"` + 레버리지 3배 + 롱/숏**을 하드코딩하고 있었다. 미국·코스피·코스닥
**주식은 현물**이라 "AMZN 3배 숏" 같은 선물 포지션은 성립하지 않는다(공매도·레버리지가 개인
모의 관점에서 비현실적). 코인만 무기한선물(perp)이 있어 롱/숏+레버리지가 자연스럽다.

### 바뀐 것 (FE 전용)

- `app/signals/[id]/page.tsx`의 `SignalActionPanel` — `signal.market === "CRYPTO"`로 분기.
  - **코인**: 기존대로 수량+레버리지 입력, `Paper Long`/`Paper Short`(FUTURES, position_side+leverage).
  - **주식**: 수량만(레버리지 입력 숨김), 단일 `모의 매수 (현물)`(SPOT BUY, position_side·leverage
    미전송) + "주식은 현물 매수만 지원(레버리지·공매도 없음)" 안내.
- 백엔드 무변경(계약상 SPOT|FUTURES 모두 허용 — 이번엔 FE가 시장에 맞는 기본값을 낼 뿐).

### 검증

- `tsc --noEmit`·`next build` 통과.
- 라이브 실측: 주식 신호(Liberty Media, US·TOP) 상세가 단일 "모의 매수 (현물)"+레버리지 없음으로
  렌더(스크린샷 확인). `POST /paper/orders {investment_type:SPOT, side:BUY}`(AMZN) → **201 FILLED,
  position_side=null, leverage=1**. 검증 후 모의계정 리셋으로 원복(positions 0, cash 복구).
- FE 재기동 후 라이브 Chrome full smoke **0에러**.
