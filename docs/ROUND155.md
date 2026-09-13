# VEIN Round 155

Date: 2026-09-13

## 틱띄기 매수/매도 비율(ScalpSidecarClient.ratio) 단위 테스트 (BE, 회귀 보호)

자율 루프 R155. 틱띄기(스캘핑) 상세의 매수/매도 비율 표기 `ratio(part, total)`
(소수 4자리 HALF_UP)는 순수 로직인데 테스트가 없었다. 호출부가 `total==0`을 미리
null 처리하므로 0분모는 도달 불가 — 정상 케이스만 회귀 보호.

### 바뀐 것

- `ScalpSidecarClient.ratio`: `private static` → package-private `static`(동작 무변경).
- 신규 `ScalpSidecarClientTest` (+3):
  - 4자리 포맷(1/4=0.2500, 3/4=0.7500, 5/5=1.0000).
  - 0/5=0.0000.
  - HALF_UP 반올림(1/3=0.3333, 2/3=0.6667).

### 검증

ASCII 경로 복사본 `gradlew test --tests ScalpSidecarClientTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
