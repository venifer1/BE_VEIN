# VEIN Round 78

Date: 2026-09-12

## 비관리자의 admin 경로 접근 500 → 403 정규화 (BE, 견고성)

자율 루프 R78. 신규(TESTER) 유저 토큰으로 GET 경로를 훑는 실측(R49·R58·R59에서 검증된
버그 발굴 방식)에서 **`/admin/**` 전 엔드포인트가 비관리자에게 500**을 반환하는 것을 발견했다.
`/admin/overview`·`/admin/users`·`/admin/audit-logs`·`/admin/delivery-attempts/failed` 모두
`{"code":"INTERNAL_ERROR"}` 500 + ERROR 로그를 냈다.

### 원인

- `AdminController`는 `@PreAuthorize("hasRole('SUPER_ADMIN')")`(메서드 시큐리티,
  `@EnableMethodSecurity`)로 권한을 강제한다.
- `SecurityConfig`의 필터단은 `.anyRequest().authenticated()`만 걸고 `accessDeniedHandler`를
  두지 않아, **인증은 통과**한 비관리자 요청이 컨트롤러 진입 직전 메서드 시큐리티에서
  `AuthorizationDeniedException`(= `AccessDeniedException` 하위)을 던진다.
- 이 예외는 시큐리티 필터가 아니라 **DispatcherServlet까지 전파**되는데,
  `GlobalExceptionHandler`에 전용 핸들러가 없어 catch-all `handleGeneric(Exception)`에 걸려
  500 + `log.error("Unhandled exception")`로 샜다. (R43/R49가 4xx·404를 정규화했지만 403은 빈틈)

### 바뀐 것

- `GlobalExceptionHandler`에 `@ExceptionHandler(AccessDeniedException.class)` 추가 →
  `ErrorCode.FORBIDDEN`(403) + 앱 에러 봉투, **ERROR 로그 미출력**(예상된 클라이언트 권한
  거부라 로그 오염 방지). 부모 타입을 잡으므로 메서드 시큐리티의 `AuthorizationDeniedException`도
  함께 커버된다. 미인증은 기존대로 시큐리티 `authenticationEntryPoint`가 먼저 401을 낸다.
- 신규 코드/스키마 없음. `ErrorCode.FORBIDDEN`은 이미 존재(재사용).

### 검증

- 단위: `GlobalExceptionHandlerTest`에 `accessDenied_maps_to_403` 추가 → **전체 그린**
  (ASCII 경로 `C:\vein_be`, `gradlew test` BUILD SUCCESSFUL, 회귀 없음).
- 라이브(백엔드 재기동 후): 신규 TESTER 유저(id 32) 토큰으로
  `/admin/overview|users|audit-logs|delivery-attempts/failed` → **전부 403 `FORBIDDEN`**(기존 500),
  `demo@vein.local`(SUPER_ADMIN) → `/admin/overview` **200 유지**, 미인증 → **401 유지**.
  재현 후 백엔드 로그에 `Unhandled exception` **0건**(로그 오염 해소 확인).
- 프론트 무변경 → 라이브 Chrome full smoke **0에러**.
