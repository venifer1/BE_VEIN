# VEIN Round 79

Date: 2026-09-12

## Explain 피드백 `helpful` 필수화 — 빈 바디가 "아쉬워요"로 오염되던 것 수정 (BE, 무결성)

자율 루프 R79. R78의 GET 훑기에 이어 **쓰기(POST/PATCH/PUT/DELETE) 엔드포인트에 빈/불량
바디를 넣는 실측 스윕**을 돌렸다. 대부분(`/scanner/*`·`/backtests/run`·`/strategies`·`/alerts`·
`/paper/*`·삭제·존재하지 않는 리소스)은 R43/R58/R59 덕에 **전부 400/404로 견고**했다. 단
`POST /explain/{signalId}/feedback`에 빈 바디 `{}`가 **200으로 통과**하며 피드백이 조용히
기록되는 것을 발견했다.

### 원인

- `ExplainFeedbackService.Request`가 `record Request(boolean helpful, String reason)` —
  `helpful`이 **primitive boolean**이라 클라이언트가 `helpful`을 생략하면 Jackson이 기본값
  `false`로 채운다. 즉 빈 바디 `{}`가 **"아쉬워요"(helpful=false)로 저장**되어 R22가 만든
  유용률(`helpful_rate`) 지표를 오염시킨다. `@Valid`는 붙어 있었지만 primitive라 검증할 null이 없었다.

### 바뀐 것

- `Request(@NotNull Boolean helpful, String reason)` — 래퍼 타입 + `@NotNull`. `helpful` 누락/
  null이면 컨트롤러 `@Valid`가 `MethodArgumentNotValidException` → `GlobalExceptionHandler`가
  **400 VALIDATION_ERROR**(field=`helpful`)로 매핑. 서비스의 `request.helpful()`은 @NotNull
  보장 하에 primitive로 오토언박싱되므로 `create`/`update` 시그니처는 그대로. 신규 스키마 없음.

### 검증

- 단위: `ExplainFeedbackRequestTest`(jakarta Validator 직접 구동) — null helpful → `helpful`
  제약 위반 1건, 명시값(true/false) → 위반 0건. **전체 그린**(ASCII 경로 `C:\vein_be`).
- 라이브(재기동 후): `{}` → **400**(`field_errors:[{helpful, "널이어서는 안됩니다"}]`),
  `{"helpful":null}` → **400**, `{"helpful":true}` → **200**(정상 기록, `helpful_rate=100.0`).
  검증 중 생성한 행은 삭제로 원복.
- 프론트 무변경(FE는 항상 helpful을 명시 전송) → 라이브 Chrome full smoke **0에러**.
