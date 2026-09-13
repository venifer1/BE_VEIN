# VEIN Round 201

Date: 2026-09-13

## API_CONTRACT 경로 드리프트 교정: 내 정보 `/me` → `/users/me` (BE, 문서)

자율 루프 R201. 테스트 커버리지 소진 구간이라 문서 정합성 점검으로 전환. 메모리에 플래그된
"잔여 미문서 엔드포인트"를 실측 대조한 결과 대부분 이미 문서화됐으나, **내 정보 조회 경로가
실제와 어긋난 것**을 발견.

### 결과

- `UserController`는 `@RequestMapping("/api/v1/users")` + `@GetMapping("/me")` → 실제 경로
  **`/api/v1/users/me`**. 그런데 API_CONTRACT는 `GET /me`로 기재 → 소비자가 `/api/v1/me`를
  호출하면 **404**.
  - 대조: `/me/onboarding`·`/me/entitlements`·`/me/notification-prefs`는 각 컨트롤러가 실제로
    `/api/v1/me/...`라 **정확**(교정 불필요).
- API_CONTRACT §1 라인 교정: `GET /me` → **`GET /users/me`** + 응답(`UserDto`)·미인증
  `401 AUTH_INVALID` 명시.

### 검증

문서 전용(코드 무변경). 실제 컨트롤러 매핑 대조로 확인.
