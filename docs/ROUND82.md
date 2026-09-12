# VEIN Round 82

Date: 2026-09-12

## 시간 만료 신호를 활성 후보에서 제외 (BE, read-model 정합성)

자율 루프 R82. R81(홈 dedup) 검증 중 top의 상위가 전부 **score 100 IMALOL**임을 이상히 여겨
DB를 확인하니, 활성 상태(DETECTED/NEAR_COMPLETION) **111건 중 58건이 이미 `expires_at` 경과**
인데도 상태가 active로 남아 있었다. 상태 전이(→EXPIRED)는 스케줄러가 하는데, **스케줄러 실행
사이 지연**(또는 인제스트/스케줄러 게이트 off) 동안 시간상 만료된 신호가 `/signals/top`·활성
목록에 **최신 후보로 노출**된다. 실제로 홈 "오늘의 주목 신호"가 3개월 지나 만료된 IMALOL을
score 100으로 최상단에 띄우고 있었다.

### 바뀐 것

- `PatternSignalRepository.findTopByScore` — 조건에 `(s.expiresAt is null or s.expiresAt > :now)`
  추가. top은 본질적으로 "지금 살아있는 후보"이므로 항상 만료 제외.
- `PatternSignalRepository.findPage` — `activeOnly` 분기에 동일 만료 가드 추가. R54가 "만료 신호
  상단 노이즈"를 숨기려 status로 걸렀지만 status 미전이분은 새던 것을 메운다(activeOnly=false
  기본 목록은 그대로 전체 노출 — 하위호환).
- `SignalService.top()`/`list()`가 `Instant.now()`를 넘김. read 전용, 상태를 DB에서 바꾸지 않음
  (비파괴적, 전이는 여전히 스케줄러 담당). 계약(DTO) 무변경.

### 검증

- 단위: 기존 `SignalServiceTopTest`(dedup) 그린 유지. 쿼리 JPQL은 기동 시 Hibernate 검증 통과.
- 라이브(재기동 후): DB 활성 111 → 만료제외 53. `/signals/top`이 만료된 IMALOL(score 100) 대신
  실제 유효 후보(AMZN·CI·ABBV·LLY 등 TOP/ABC 1w)를 반환. `active_only=true` 목록도 동일 가드
  적용(기본 목록 `active_only=false`는 전체 유지). 프론트 무변경 → 라이브 Chrome smoke **0에러**.

### 참고

- 근본 전이(상태 컬럼 갱신)는 스케줄러 몫이며, 이 라운드는 read-model이 스케줄러 지연에 견디도록
  하는 방어선이다(정본은 여전히 `SignalStatusTransitionService`).
