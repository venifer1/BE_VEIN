# VEIN Round 77

Date: 2026-09-12

## 무효화 "실질가"(R53 완충) 노출 — 신호 상세 (BE+FE)

자율 루프 R77. 사용자가 초기에 지적한 "C(또는 A/B 저점)를 아주 조금만 이탈해도 무효화되는 것 같다"
→ R53에서 **저가-이탈 완충 버퍼**(기준선 × (1−buffer), 기본 3%)를 넣어 노이즈성 이탈은 봐주게
했다. 그런데 신호 상세 UI의 "무효화 가격"은 여전히 **raw 기준선**만 보여줘서, 실제로는 3% 아래에서야
무효화된다는 사실이 사용자에게 안 보였다. 이 정보 갭을 메웠다.

### 바뀐 것

**백엔드**
- `SignalStatusTransitionService`
  - `breaches()`의 임계선 공식을 `thresholdPrice(price, buffer) = price × (1 − buffer)`로 추출
    (`breaches`와 실질가 노출이 동일 공식 공유, DRY).
  - `invalidationBufferPct()` 게터, `effectiveInvalidationPrice(signal)` 추가 — 저가-이탈 규칙
    (`A_LOW_BREAK`/`B_LOW_BREAK`)이고 기준선이 있을 때만 실질가 반환, 그 외 `null`.
- `SignalDetailDto.InvalidationDto` → `(rule, price, bufferPct, effectivePrice)`로 확장
  (Jackson SNAKE_CASE → `buffer_pct`/`effective_price`). 완충 없는 규칙엔 두 필드 모두 null.
- `SignalService`가 `transitionService`를 주입받아 DTO에 실질가·버퍼를 채움.

**프론트**
- `lib/types.ts` `Invalidation`에 `buffer_pct`/`effective_price` 추가.
- 신호 상세 "무효화 기준" 카드: "무효화 가격" 라벨을 **기준선**으로 바꾸고, 실질가가 있으면
  **실질 무효화가 (−3% 완충)** 행 + "노이즈성 하락은 봐주고 실질가 아래로 내려가야 무효" 설명.
- R:R "무효화까지" 거리·손익비를 **실질가 우선**(있으면)으로 계산 — 실제 손실 지점 반영.
- `lib/mockData.ts` ABC(A_LOW_BREAK) 목데이터에 `buffer_pct`/`effective_price` 반영.

### 검증

- 백엔드 `compileJava` 통과. `SignalLowBreakTest`에 `thresholdPrice` 케이스 추가, **6/6 통과**
  (ASCII 경로 `C:\vein_be`).
- 백엔드 재기동 후 실측: `GET /signals/275`(A_LOW_BREAK) → `{price:"89.25", buffer_pct:"0.03",
  effective_price:"86.5725"}` (= 89.25 × 0.97) 정확.
- 프론트 `tsc`·`next build` 통과. BE·FE 재기동 후 **라이브 Chrome full smoke 0에러**.
