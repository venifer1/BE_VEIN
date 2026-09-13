# VEIN Round 123

Date: 2026-09-13

## FE 테스트 alias 설정 + extractError 커버리지 (FE, 회귀 보호)

자율 루프 R123. R122의 vitest 도입에 이어, aliased 모듈(`@/...`)도 테스트할 수 있게
`vitest.config.ts`에 `@` → 루트 alias를 추가하고, 전 화면 에러 표시의 진입점인
`extractError`(에러 봉투 파싱)를 테스트했다.

### 바뀐 것 (프론트, dev 전용)

- `vitest.config.ts`: `resolve.alias` `@`→프로젝트 루트(tsconfig `@/*`→`./*` 대응),
  node 환경, include `lib/**` + `store/**`. → 이제 `@/store`·`@/lib` 등 aliased import를
  쓰는 모듈도 테스트 가능(인프라 확장).
- `lib/api.test.ts` (4): 서버 에러 봉투는 그대로 반환, 봉투 없으면 `NETWORK_ERROR`+axios
  message, null/undefined는 기본 메시지, error 필드 없는 response도 NETWORK_ERROR.

### 검증

`vitest run` **18/18 그린**(format 9·types 5·api 4). `tsc`·`next build` 통과(dev 전용, 빌드
무영향). api.test는 `@/store/auth`를 당기는 api.ts를 alias로 정상 해석.
</content>
