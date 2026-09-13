# VEIN Round 183

Date: 2026-09-13

## 가입 메타데이터 정리 헬퍼(AuthService.trimTo) 단위 테스트 (BE, 회귀 보호)

자율 루프 R183. 공개 가입(R35) 시 유입추적 컬럼(utm 등)을 저장 전에 정리하는 `trimTo`
(strip → 빈 값 null → max 길이 방어)는 순수 로직인데 테스트가 없었다.

### 바뀐 것

- `AuthService.trimTo`: `private static` → package-private `static`(동작 무변경).
- 신규 `AuthServiceTest` (+3):
  - null·공백 → null.
  - 앞뒤 공백 strip("  hello  "→"hello").
  - max 초과 절단(10자→5자), 이하 유지, **strip 먼저 후 길이 제한**("  abcdefg  ",3→"abc").

### 검증

ASCII 경로 복사본 `gradlew test --tests AuthServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
