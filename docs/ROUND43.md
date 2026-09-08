# VEIN Round 43

Date: 2026-09-08

## 견고성 — 잘못된 요청의 4xx 정규화 + 입력 검증 (Track B #4)

혼자 쓰는 경로만 밟아 잠재돼 있던 엣지케이스 중, **클라이언트의 잘못된 요청이 500으로
새는** 문제를 잡는다. 소유권(IDOR) 경계는 이미 alerts·notifications·scanner·paper 전부
`userId` 스코핑/`owned()` 체크로 견고함을 감사로 확인했다 — 이번엔 요청 검증 레이어.

### 문제

기존 `GlobalExceptionHandler`는 `ApiException`·`MethodArgumentNotValidException`(바디 검증)만
잡고 나머지는 `Exception` catch-all → **500 INTERNAL_ERROR**로 떨어졌다. 그래서:

- `?days=abc`, `?type=FOO`(enum 파싱 실패), `?limit=xyz` → 500 (400이어야)
- 필수 파라미터 누락 → 500 (400이어야)
- 깨진/빈 JSON 바디 → 500 (400이어야)
- 잘못된 HTTP 메서드 → 500 (405여야)

500은 서버 잘못이라는 신호라 로그를 오염시키고, FE가 "재시도 가능한 서버 오류"와
"고쳐야 할 클라이언트 오류"를 구분하지 못한다.

### 추가된 것 (`com.vein.common`)

- `GlobalExceptionHandler`:
  - `MethodArgumentTypeMismatchException`·`MissingServletRequestParameterException`·
    `HttpMessageNotReadableException` → **400 VALIDATION_ERROR**(+ 문제 필드).
  - `ConstraintViolationException`(@Validated 파라미터) → **400**.
  - `HttpRequestMethodNotSupportedException` → **405 METHOD_NOT_ALLOWED**.
- `ErrorCode.METHOD_NOT_ALLOWED(405)` 추가.
- `NotificationPrefDto` 조용한 시간(R42) 값 `@Min(0)@Max(23)` + 컨트롤러 `@Valid` →
  시각이 범위를 벗어나면 **400**(기존엔 엔티티에서 조용히 clamp만).

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- **단위 테스트 `GlobalExceptionHandlerTest` 4/4 통과**(잘못된 파라미터→400·필드,
  누락→400, 제약위반→400, 잘못된 메서드→405). 순수 단위(DB/컨텍스트 불필요).
  - ※ 한글+공백 경로에서 `gradlew test`는 워커 @argfile 인코딩 버그로 클래스 로드 실패
    (코드 결함 아님, `chcp`로도 포크된 워커엔 미적용). **ASCII 경로 복사본(C:\vein_be)에
    공유 Gradle 캐시로 실행 → BUILD SUCCESSFUL** 로 확인.
- FE 변경 없음(에러 봉투 형태 동일) → FE 스모크 회귀 대상 없음.

### 이번에 발견한 별개 이슈 (R43 범위 밖 · 미수정)

- 전체 스위트 실행 시 **`AbcDetectorTest.allowsShortABWhenBIsHighestAfterA()` 1건 실패**
  (37 중 1). R43는 패턴 코드를 건드리지 않았고, ABC 파라미터/로직은 레거시에서 포팅한
  **사용자 튜닝 영역**이라 임의 수정하지 않음. `patterns` size는 1로 맞으나 `idxA`가 4가
  아님 — 탐지기 로직 변경 or 테스트 기대값 드리프트로 보임. **별도 판단 필요**(탐지기가
  맞는지 테스트가 맞는지). 나머지 36건은 통과.
