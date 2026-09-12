# VEIN Round 54

Date: 2026-09-12

## 스캐너 기본 "활성 신호만" (UI 감사에서 발견)

전 화면 UI 감사(데모 유저, 430px) 중 발견: **스캐너 패턴 신호 목록이 상태 필터 없이 `detected_at`
내림차순**이라, 최근 생성됐다 **만료(EXPIRED)된 신호가 활성 신호보다 상단**에 뜬다. 핵심 화면인데
죽은 신호가 목록 맨 위를 차지해, "지금 후보"를 보러 온 사용자에게 노이즈였다.

### 바뀐 것

- **백엔드**: `PatternSignalRepository.findPage`에 `activeOnly` 파라미터 추가 →
  `s.status in (DETECTED, NEAR_COMPLETION)`. `SignalService.list`·`GET /signals?active_only=`
  (기본 `false`로 API 하위호환 유지). 다른 소비자(홈 `signals/top`은 이미 활성만, 관심종목 최근신호)
  영향 없음.
- **프론트**: 스캐너가 **기본 `active_only=true`**(만료·무효 숨김). 필터바에 "만료 포함" 토글 추가 —
  켜면 `?all=true`로 전체 상태 노출(이력 확인용). URL 파라미터 `all`로 상태 유지. mock `/signals`도
  `active_only` 필터 반영(계약 패리티).

### 검증

- 백엔드 `compileJava` 성공. 프론트 `typecheck`/`build` 통과.
- **라이브**:
  - `GET /signals` 기본 → 상단에 EXPIRED 포함(기존 동작 유지, 하위호환).
  - `GET /signals?active_only=true` → DETECTED/NEAR_COMPLETION만, EXPIRED/INVALIDATED 0건.
  - 스캐너 화면: 기본은 "탐지" 신호만(만료 이더리움·체인링크 사라짐), "만료 포함" 토글 시 복원
    (만료 배지 카운트 default 0개 → all 3개). 스크린샷 육안 확인.
  - **Chrome smoke 0에러**. 첫 신호 딥링크도 활성 신호를 따라가 상세 검증이 더 정확해짐.

### 그 외 감사에서 눈에 띈 것 (후속 후보, 이번엔 미변경)

- 모의투자 초기 잔액 입력이 천단위 구분 없이 `10000000` — 가독성 개선(콤마/한글 단위) 여지.
- 신호 카드의 표본 부족 적중률(예: 표본 3, 100%)은 이미 Confidence 체계가 있으나 목록 strip엔
  경고가 옅다.
