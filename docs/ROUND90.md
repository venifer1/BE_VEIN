# VEIN Round 90

Date: 2026-09-13

## 홈 "오늘의 주목 신호"를 종합 Pattern Score로 정렬 (BE, R81 백로그 해소)

자율 루프 R90. `/signals/top`(홈 주목신호)은 R41 이후 **구조 점수(score, 탐지기 완성도)**로만
정렬했다. 하지만 실제 큐레이션 가치는 완성도에 거래량·추세·변동성·뉴스를 합산한 **종합
Pattern Score**(Explain이 계산하는 100점 만점)에 있다. R81이 이 정렬 개선을 "pattern_score
영속화 선행 필요"라며 백로그로 남겼던 것을 이번에 처리했다.

증상(실측): 구조점수 정렬 top6에 신호 140(ABC/KOSPI, 구조 92.2)이 상단이었는데 종합
점수는 46(거래량·추세·뉴스가 약함)이었다. 즉 "탐지기 구조만 예쁘고 실전 근거는 빈약한"
신호가 홈 상단을 차지했다.

### 바뀐 것

- **V29** — `pattern_signals.pattern_score numeric(6,2)`(nullable) + 활성 신호 정렬용 부분 인덱스
  `ix_signals_pattern_score_active(coalesce(pattern_score,score) DESC, detected_at DESC)`.
- **`PatternSignal.patternScore`** 필드 추가.
- **`SignalExplainService.computeScore(signal)`** 신설 — Explain 전체를 만들지 않고 종합 점수
  총점만 반환. 입력 수집·계산을 `explain()`과 **동일 경로(`assess`)로 공유**해 두 값이 절대
  어긋나지 않게 리팩터(동작 보존, 전체 테스트 그린).
- **`SignalPatternScoreService.refreshActive()`** — 활성 신호(DETECTED/NEAR_COMPLETION)의
  pattern_score를 계산·저장(멱등, per-signal try/catch). 지표·뉴스는 DB 읽기라 ingestion 불필요.
- **`SignalPatternScoreScheduler`** — 기동 시 1회(백그라운드 스레드, 부팅 비차단) + 주기(기본
  15분, ShedLock). **ingestion 게이트와 무관**(전용 플래그 `vein.signal.pattern-score.enabled`,
  기본 on)이라 스캔을 안 켜도 top이 최신 근거로 유지된다.
- **`findTopByScore`** 정렬 키를 `coalesce(pattern_score, score) desc`로 변경 —
  종합 점수가 있으면 그걸로, 미계산이면 구조 점수로 폴백(하위호환). API/DTO 무변경(정렬만).

### 검증 (라이브)

- Docker(PG 16·Redis 7) 기동, 실데이터(캔들 295,840·신호 316) 위에서 bootRun.
- Flyway **V29 적용 성공**(success=t). 기동 백필 로그 `pattern-score: wrote/updated 111 rows`,
  DB 활성 111건 전부 pattern_score 채워짐.
- 정렬 대조: 구조점수 top6 = [88,292,140,97,362,285] → 종합 top6 = [292,88,94,137,97,285].
  구조 92.2·종합 46이던 **140이 탈락**하고 종합점수 높은 94(68)·137(67)이 진입.
- `GET /signals/top?limit=6`(임시 토큰) = [292,88,94,137,97,285]로 종합 순서 반환 확인.
- 전체 테스트 그린(ASCII 경로 복사본, Explain 리팩터 회귀 없음). 검증 후 임시 유저 정리.

### 남긴 것 / 참고

- pattern_score는 API/DTO에 노출하지 않았다(이 라운드의 가치는 **정렬**). 필요하면 후속에서
  SignalDto에 추가 가능.
- 탐지 직후 신호는 다음 스케줄/기동 때 채워진다(스캔 핫패스와 분리 — 성과 추적과 동일 철학).
</content>
