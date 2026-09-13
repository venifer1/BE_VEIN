# VEIN Round 184

Date: 2026-09-13

## 웹푸시 구독 필드 정리(WebPushService normalize/trimToNull) 단위 테스트 (BE, 회귀 보호)

자율 루프 R184. 웹푸시 구독 저장 시 필드를 정리하는 `normalize`(필수: null→""+trim)·
`trimToNull`(선택: 빈 값→null+trim)은 순수 로직인데 테스트가 없었다.

### 바뀐 것

- 두 메서드: `private static` → package-private `static`(동작 무변경).
- 신규 `WebPushServiceTest` (+2):
  - `normalize`: null/""→"", "  abc  "→"abc".
  - `trimToNull`: null/공백→null, "  xyz  "→"xyz".

### 검증

ASCII 경로 복사본 `gradlew test --tests WebPushServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
