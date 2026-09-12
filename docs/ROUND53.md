# VEIN Round 53

Date: 2026-09-12

## 저가-이탈 무효화 완화 버퍼 (사용자 요청 · ABC/TOP 튜닝)

사용자 관찰: ABC 신호가 기준선(A 저점)을 **1틱만 찔러도 즉시 무효**라, 잠깐 스치는 꼬리(wick)나
노이즈성 이탈에도 죽어버린다 — "어느 정도(3~5%)는 봐줘야 한다".

### 바뀐 것 (`SignalStatusTransitionService`)

- 저가-이탈 무효화(ABC `A_LOW_BREAK` · TOP `B_LOW_BREAK`, 같은 코드 경로)에 **완화 버퍼** 적용.
  기존: `low < 기준선`이면 무효. 변경: `low < 기준선 × (1 − buffer)`일 때만 무효.
- 순수 정적 `breaches(low, invalidationPrice, bufferPct)` 로 분리(테스트 용이). null·음수 버퍼는
  안전 처리(음수→0). `buffer=0`이면 기존 동작(1틱 이탈=무효) 그대로.
- 저가(low) 기준은 유지 — 버퍼가 충분히 커 꼬리를 흡수한다. 기준선(무효화 가격 = A/B 저점)은
  그대로라 차트 표시선도 불변(버퍼는 트리거 여유일 뿐).
- 값은 `application.yml` `vein.signal.invalidation-buffer-pct`(env `SIGNAL_INVALIDATION_BUFFER_PCT`,
  **기본 0.03 = 3%**)로 분리. 5%로 올리려면 `0.05`.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `SignalLowBreakTest` 5/5**(버퍼 내 얕은 이탈
  봐줌 · 버퍼 초과 무효 · 정확히 A 터치는 무효 아님 · buffer=0 strict · null/음수 안전).
  ASCII 경로 **BUILD SUCCESSFUL**.
- 라이브: bootRun 정상(버퍼 설정 로드), `/actuator/health` 200. 실제 전이는 다음 신호 재평가
  사이클부터 적용(활성 ABC/TOP 신호에 반영).
- FE 무변경(무효화는 서버측 판정, 표시선·계약 동일).

### 메모

- 기본 3%는 사용자 제시 범위(3~5%)의 하단(무효를 조금 더 이르게 = 죽은 패턴을 덜 오래 살림).
  더 관대하게 원하면 env로 `0.05`. ABC만 완화하고 TOP은 제외하고 싶으면 별도 분리 필요(현재는
  공통 저가-이탈 경로라 함께 적용).
