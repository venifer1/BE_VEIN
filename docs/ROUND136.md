# VEIN Round 136

Date: 2026-09-13

## 조건검색 비교 로직 추출 + 단위 테스트 (BE, 회귀 보호)

자율 루프 R136. 조건검색 스캐너의 핵심인 연산자 비교(`left <op> right`)가 `evaluate`
메서드에 인라인돼 테스트 불가였다. 순수 static `compare`로 추출 + 테스트.

### 바뀐 것

- `ConditionScannerService.compare(left, operator, right)` 추출(순수·package-private). `evaluate`는
  이를 호출. null 피연산자/연산자 또는 미지원 연산자는 false(매칭 안 됨).
- `ConditionScannerServiceTest` (5): `< <= > >=` 경계 포함, null/미지원 연산자.

### 검증

ASCII 경로 복사본 `gradlew test --tests ConditionScannerServiceTest` **그린**. 동작 동일
(추출 리팩터, 로직 불변).
</content>
