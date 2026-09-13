# VEIN Round 173

Date: 2026-09-13

## 공통 에러 봉투(ApiError) 빌더 단위 테스트 (BE, 회귀 보호)

자율 루프 R173. R172의 성공 봉투에 이어 에러 봉투(부록 C-1) `ApiError.of`를 보강. 빈/누락
`field_errors`를 null로 접어(NON_NULL) 응답 JSON에서 키가 사라지게 하는 분기가 무테스트였다.

### 바뀐 것

- 신규 `ApiErrorTest` (+3, 코드 변경 0):
  - null·빈 리스트 fieldErrors → null로 접힘.
  - 비어있지 않은 fieldErrors → 그대로 유지.
  - code·message·traceId 정확 전달.

### 검증

ASCII 경로 복사본 `gradlew test --tests ApiErrorTest` **BUILD SUCCESSFUL**. 코드 무변경.
