# VEIN Round 48

Date: 2026-09-12

## 온보딩 "시작하기" 체크리스트 (Track B #2)

Track B(남에게 보여주려면)의 첫 라운드. 지금까지는 시드 계정 하나로만 돌아, 신규 사용자가
가입 직후 **빈 화면 앞에서 뭘 해야 할지 모르는** 문제가 있었다. 홈 상단에 핵심 기능으로
안내하는 "시작하기" 카드를 추가한다.

### 설계 — 상태에서 파생, 저장 안 함

스텝 완료 여부는 별도 플래그가 아니라 **각 기능의 실제 데이터 존재**에서 파생한다. 그래서
사용자가 해당 기능을 실제로 쓰면 자동으로 체크되고, 어긋날 여지가 없다:

- **WATCHLIST** — 관심종목 항목 ≥ 1 (`watchlist_items` count)
- **ALERT** — 알림 규칙 ≥ 1 (`alerts` exists)
- **PAPER** — 모의투자 계좌 존재 (`paper_accounts` exists)
- **SCANNER** — 조건검색식 ≥ 1 (`scanner_rules` exists)

"닫음"만 사용자 행에 기록한다(V27 `users.onboarding_dismissed_at`). `all_done`이거나 `dismissed`면
FE가 카드를 감춘다.

### 추가 (`com.vein.onboarding`)

- `GET /api/v1/me/onboarding` → `{steps:[{key,label,done,href}], completed, total, all_done, dismissed}`.
- `POST /api/v1/me/onboarding/dismiss` — 닫은 시각 기록(멱등) 후 최신 상태 반환.
- `OnboardingSteps.build(...)` — 불리언 4개 + 닫힘으로 스텝 순서·라벨·딥링크·진행률 조립(순수 정적,
  단위 테스트).
- `OnboardingService` — 4개 repo에서 존재 여부 조회 + `users`의 닫힘 시각.
- repo 보강: `WatchlistItemRepository.countByIdWatchlistId`,
  `PaperAccountRepository.existsByUserId`, `AlertRepository.existsByUserId`,
  `ScannerRuleRepository.existsByUserId`.
- `User.onboardingDismissedAt` + `dismissOnboarding(now)`(멱등). V27 마이그레이션.

### 프론트

- 홈 상단(터미널 헤더 아래) "시작하기" 카드: 완료 스텝은 취소선+체크, 미완료는 딥링크+화살표,
  `n/총계 완료` 진행률, 닫기(X). `all_done`·`dismissed`면 렌더 안 함.
- `useOnboarding`/`useDismissOnboarding`(dismiss는 캐시 낙관 갱신). mock 계약(목 상태에서 파생 +
  dismiss 플래그) 동형.
- `smoke.mjs`에 `onboarding` 체크(홈에 "시작하기" 노출) 추가.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `OnboardingStepsTest` 5/5**(스텝 순서·라벨·
  진행률·all_done·dismissed 독립성). ASCII 경로 복사본 **BUILD SUCCESSFUL**.
- 프론트 `typecheck`/프로덕션 `build` 통과.
- **라이브**(bootRun + V27 적용 확인 + `next start`):
  - 신규 유저: `GET /me/onboarding` → `completed=0/4`, 모든 스텝 `done=false`, `dismissed=false`,
    키 순서 `[WATCHLIST, ALERT, PAPER, SCANNER]`.
  - `POST /dismiss` → `dismissed=true`.
  - **라이브 Chrome smoke 0에러**(신규 `onboarding` 체크 포함). 홈 "시작하기" 카드 4스텝 렌더
    스크린샷 육안 확인.
  - 검증 후 임시 유저 2명·데이터 삭제로 원복.

### 메모

- 로컬 검증 제약(시드 관리자 자격 드리프트 → 공개 회원가입 임시 유저, smoke용 임시 SUPER_ADMIN
  승격)은 ROUND45.md 참고.
- 다음 Track B 후보: #4 엣지케이스(나 혼자 쓰는 경로만 밟아 잠재된 것들).
