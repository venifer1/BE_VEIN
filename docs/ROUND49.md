# VEIN Round 49

Date: 2026-09-12

## 엣지케이스 — 미매핑 경로 404 정규화 (Track B #4)

R43이 "잘못된 요청(나쁜 파라미터·enum·누락·깨진 JSON·잘못된 메서드)"을 4xx로 정규화했지만,
남아 있던 빈틈을 **신규 유저로 GET 엔드포인트를 훑는 실측**으로 발견해 잡았다.

### 발견 (실측)

신규 유저 토큰으로 주요 경로를 훑던 중, **인증을 통과한 미매핑 경로가 전부 500**을 내는 것을
확인했다:

```
GET /api/v1/watchlist      → 500 INTERNAL_ERROR   (정답: /watchlists/default)
GET /api/v1/paper/account  → 500 INTERNAL_ERROR   (그런 경로 없음)
GET /api/v1/nonexistent    → 500 INTERNAL_ERROR
GET /api/v1/foo/bar        → 500 INTERNAL_ERROR
```

원인: 미매핑 경로는 `NoResourceFoundException`(Spring 6.1)을 던지는데, 이게 `@ExceptionHandler(
Exception.class)` **catch-all에 걸려 500 + ERROR 로그**로 샜다. 즉 오타 URL·존재하지 않는 리소스
같은 **클라이언트 404**가 (a) 서버 오류로 위장되고 (b) 매 요청 ERROR 로그를 남겨 로그를 오염시켰다.
FE는 "재시도 가능한 서버 오류"와 "고쳐야 할 잘못된 URL"을 구분할 수 없었다.

> 참고: 미인증 미매핑 경로는 시큐리티가 먼저 401을 내므로 무해했다. 문제는 인증 통과 후 경로였다.

### 함께 확인한 것 (이미 견고 — 무변경)

같은 훑기에서 인접 엣지는 R43 덕에 이미 정상임을 확인:

- `/signals/abc`·`/instruments/abc`(잘못된 path var) → 400 VALIDATION_ERROR
- `/notifications?cursor=garbage` → 400 INVALID_CURSOR
- `/notifications/digest?window=-5` → 1로 클램프 후 200, `?window=abc` → 400
- 없는 리소스 id → 404(SIGNAL/NOTIFICATION_NOT_FOUND)
- 신규 유저의 `/watchlists/default`(lazy 생성)·`/paper/portfolio`(계좌 없음→404 깔끔) 정상

### 수정 (`com.vein.common.GlobalExceptionHandler`)

- `@ExceptionHandler({ NoResourceFoundException, NoHandlerFoundException })` 추가 →
  **404 NOT_FOUND + 앱 에러 봉투**. catch-all(500·ERROR 로그)보다 구체적이라 우선 적용되고,
  로그 스팸도 사라진다. `ErrorCode.NOT_FOUND(404)` 재사용, 신규 코드/스키마 없음.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- **단위 `GlobalExceptionHandlerTest` 6/6**(기존 4 + 신규 2: NoResourceFound·NoHandlerFound →404).
  순수 단위, ASCII 경로 복사본 **BUILD SUCCESSFUL**.
- **라이브**(bootRun):
  - 수정 후 `/watchlist`·`/paper/account`·`/nonexistent`·`/foo/bar` → **404 NOT_FOUND**
    (`{"error":{"code":"NOT_FOUND","message":"No handler for the requested path"}}`).
  - 회귀: `/watchlists/default`·`/notifications`·`/me/onboarding` → 200 정상.
  - **라이브 Chrome smoke 0에러**(전체 스택 건강 재확인). FE 변경 없음(에러 봉투 형태 동일).
  - 검증 후 임시 유저 정리로 DB 원복.

### 메모

- FE는 계약(에러 봉투) 무변경이라 회귀 대상 없음(R43과 동일 판단).
- Track B(남에게 보여주려면): #2 온보딩(R48)·#4 엣지케이스(R49) 진행. 남은 것: #3 데이터 출처/지연
  표시 강화는 R40에서 상당 부분 됨. #1 Flutter는 사용자 지시로 보류.
