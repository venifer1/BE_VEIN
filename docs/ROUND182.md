# VEIN Round 182

Date: 2026-09-13

## 수집 실패 오류코드 분류(IngestionService.errorCodeOf) 단위 테스트 (BE, 회귀 보호)

자율 루프 R182. 캔들 수집 실패를 ingestion_runs에 기록할 때 예외를 코드 문자열로 바꾸는
`errorCodeOf`(ApiException → ErrorCode 이름, 그 외 → 클래스 단순명)는 순수 로직인데
테스트가 없었다.

### 바뀐 것

- `IngestionService.errorCodeOf`: `private` → package-private `static`(`this` 미사용,
  동작 무변경).
- 신규 `IngestionServiceTest` (+2):
  - ApiException(INVALID_QUERY) → "INVALID_QUERY".
  - IllegalStateException → "IllegalStateException", RuntimeException → "RuntimeException".

### 검증

ASCII 경로 복사본 `gradlew test --tests IngestionServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
