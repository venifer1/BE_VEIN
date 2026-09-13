# VEIN Round 147

Date: 2026-09-13

## 등락 순위(movers) 시장·타입 라우팅 + 거래대금 정렬 단위 테스트 (FE, 회귀 보호)

자율 루프 R147. 데모(mock) 등락 순위 `getMovers(type, market)`는 4개 시장 × 3타입
(GAINERS/LOSERS/VOLUME)으로 분기하고 VOLUME은 상승·하락을 합쳐 거래대금 내림차순
정렬하는데, 이 라우팅·정렬 불변식에 테스트가 없었다.

### 바뀐 것

- `lib/mockData.test.ts` +4(`getMovers` 기존 export 사용, 코드 변경 0):
  - GAINERS 전부 change_rate ≥ 0 · LOSERS 전부 < 0 (CRYPTO 기본).
  - VOLUME은 거래대금 내림차순 정렬(CRYPTO·US·KOSPI 불변식 검사).
  - market 미지정 시 CRYPTO 기본값.
  - US VOLUME은 상승+하락 병합(길이 합).
- 데이터값이 아니라 **함수 불변식**을 단언 → mock 데이터가 바뀌어도 견고.

### 검증

FE `npx vitest run` **6파일 64개 그린**(R146 60 → +4).
