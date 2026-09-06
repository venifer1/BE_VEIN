# VEIN Round 35

Date: 2026-09-06

## 공개 회원가입 + 유입 추적 (MONETIZATION 단계2 ①·③)

R34에서 비로그인 공개 리포트를 열었으니, 콘텐츠로 데려온 사람을 **받을 그릇**을
만든다. 지금까지 계정 생성 방법은 DB 직접 INSERT뿐이었고 기본 status가 PENDING
(승인제)이라 공개 베타 자체가 불가능했다.

### 추가된 것

- **`POST /api/v1/auth/signup`** (`AuthController` / `AuthService.signup`)
  - `{email, password(8~100), signup_source?, signup_referrer?}`
  - 이메일 중복 검사(`ux_users_email` 인덱스 + `saveAndFlush` 후
    `DataIntegrityViolationException` 레이스 방어) → `409 ALREADY_EXISTS`
  - BCrypt 해시(`PasswordEncoder` 재사용), role=TESTER 고정
  - `vein.signup.auto-approve`(기본 true)면 **가입 즉시 APPROVED + 토큰 발급(자동 로그인)**,
    false면 **PENDING(승인제 복귀)**. 봇 가입 급증 시 env(`SIGNUP_AUTO_APPROVE=false`)로 즉시 잠금
  - `AuditService`에 SIGNUP 감사로그
- **유입 추적** — V23 마이그레이션으로 `users.signup_source(64)` / `signup_referrer(255)` 추가.
  어느 콘텐츠/리포트가 가입을 만들었는지 측정용(둘 다 nullable, `updatable=false`)
- **레이트리밋** — `PublicRateLimitFilter`를 규칙 기반으로 일반화:
  `/api/v1/public/**` 60 req/min + `/api/v1/auth/signup` **10 req/hour**.
  초과 시 `429 RATE_LIMITED`(+`Retry-After`). 봇 가입으로 DB 오염 방지
- **`SecurityConfig`** — `/api/v1/auth/signup` permitAll
- **application.yml** — `vein.signup.auto-approve: ${SIGNUP_AUTO_APPROVE:true}`

### 검증 (실데이터 기동)

- 가입 → 200 `status=APPROVED` + access/refresh 토큰(자동 로그인)
- 중복 이메일 → `409 ALREADY_EXISTS`("이미 가입된 이메일입니다.")
- 비번 8자 미만 → `400 VALIDATION_ERROR`(field_errors)
- `signup_source` 컬럼 저장 확인, V23 컬럼 반영 확인
- IP당 10/시간 초과 → `429`, 가입 계정으로 로그인 왕복 성공
- 검증용 계정은 정리, 시드 계정만 잔존

### 남은 것 (단계2 나머지)

- **FE 비로그인 랜딩 페이지** — 실측 적중률(`/public/reports/weekly`) 노출 +
  가입 폼 + 리포트 링크 (다음)
- 후원 링크(FE 정적) — 기능 차등 없음
