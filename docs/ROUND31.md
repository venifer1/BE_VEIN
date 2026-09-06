# VEIN Round 31 Proposal

Date: 2026-06-17

## Progress

### Paper UI Polish 1

- `/paper` 화면을 계정 요약 + 탭 구조로 정리했다.
- 탭은 `주문`, `포지션`, `내역`으로 분리했다.
- 선물 주문 영역에서 LONG/SHORT, 레버리지, 청산/감소 상태가 한 화면에 보이도록 재배치했다.
- 보유 선물 포지션이 있을 때 25/50/100% 빠른 청산 수량 버튼을 추가했다.
- 포지션 카드에 진입가, 마크가, 증거금, ROE, 미실현 손익을 표시했다.
- 주문 내역은 상태, 투자유형, 수량, 가격을 분리해 읽기 쉽게 정리했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.
- `npm run start` foreground boot: passed and reported `http://localhost:3000`.
- Background `Start-Process` launch did not leave port `3000` listening in this shell environment, so persistent local server state should be checked manually if needed.

### Signal Action Flow 1

- 신호 상세 상단에 `다음 액션` 카드를 추가했다.
- 신호 종목 기준으로 Paper Futures `LONG` / `SHORT` 빠른 모의 주문을 생성할 수 있게 했다.
- 수량과 레버리지를 신호 상세에서 바로 입력할 수 있게 했다.
- Explain `Risk Guard`가 `BLOCK`이면 Paper Long/Short 버튼을 비활성화한다.
- 백테스트 버튼은 `/scanner?tab=backtest`로 이동하도록 연결했다.
- 스캐너 화면은 `?tab=backtest` 초기 진입을 지원하도록 보강했다.
- 모의 화면 버튼은 `/paper`로 이동한다.
- 모의 계정이 없어 주문이 실패하는 경우 안내 문구를 표시한다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Alert Center UI 1

- `/settings` 화면을 알림 센터 중심으로 재구성했다.
- 상단에 활성 알림 규칙, 안읽은 알림, 실패 알림 요약 카드를 추가했다.
- 웹 푸시 상태를 별도 카드로 정리하고 활성/비활성, 브라우저 권한, 서버 준비 상태를 배지로 표시했다.
- 알림 규칙 목록에서 종목, 패턴, timeframe, market, 쿨다운, 활성 상태를 더 명확히 표시했다.
- 알림함에 `전체`, `안읽음`, `생성`, `실패` 필터를 추가했다.
- 알림 항목에 상태 배지, 상대 시각, 종목, 신호 타입을 표시했다.
- 기존 깨진 한글 문구를 설정 화면 범위에서 정상 문구로 정리했다.
- 시스템 상태, 계정, 진단 영역은 알림 센터 아래로 재배치했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Scanner Rule Quality UI 1

- 조건검색 화면을 템플릿 중심으로 재구성했다.
- 기본 템플릿을 추가했다:
  - 거래량 급증
  - RSI 반등 후보
  - MA20 돌파
  - MACD 전환
- 현재 조건을 사람이 읽을 수 있는 요약 문자열로 표시했다.
- 검색 조건의 품질 배지를 추가했다:
  - 무매칭이면 조건 완화 안내
  - 과다매칭이면 조건 축소 안내
  - 적정 범위면 양호 표시
- 검색 결과에 충족률을 표시했다.
- 결과 카드에 매칭된 조건 배지를 표시했다.
- 저장된 검색식 목록을 활성 상태 우선으로 정렬했다.
- 저장식 카드에 조건 요약, 최근 실행 시각, 충족률, 빈도 등급, 알림 수를 표시했다.
- 저장식 복제 액션을 추가했다.
- 기존 깨진 한글 문구를 조건검색 화면 범위에서 정상 문구로 정리했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Admin Operations UI 1

- `/admin` 화면의 깨진 한글 문구를 정상 문구로 정리했다.
- 운영 지표 카드를 위험도 톤과 함께 재정렬했다.
- 승인 대기 사용자, 실패한 전달, 읽지 않은 알림, 활성 조건검색 매칭을 `운영 큐`로 묶었다.
- 실패한 알림/웹푸시 지표가 있으면 경고 또는 오류 톤으로 보이도록 했다.
- 조건검색식 카드에 활성 수, 매칭 수, 최근 24h 실행 수를 더 명확히 표시했다.
- 감사 로그, 최근 알림, 실패한 전달 목록의 문구와 구분자를 정리했다.
- 사용자 관리 버튼 문구를 `승인`, `잠금`으로 정리했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Common UI Text Polish 1

- 공통 배지 컴포넌트의 깨진 한글 라벨을 정상화했다.
- 신호 상태 라벨을 정리했다:
  - 탐지
  - 완성 임박
  - 무효
  - 만료
  - 종료
- 시장 라벨을 정리했다:
  - 코인
  - 미국
  - 코스피
  - 코스닥
- 신호 타입 라벨을 정리했다:
  - ABC
  - 고점되돌림
  - 이말올
- 뉴스 감성 라벨을 긍정/부정/중립으로 정리했다.
- 공통 상태 컴포넌트의 오류, 지연, 부분 실패, 다시 시도, 새로고침 문구를 정상화했다.
- 공통 투자 고지를 `투자 참고용 · 투자권유 아님`으로 정리했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Scanner And Signal List Text Polish 1

- `/scanner` 화면의 깨진 한글 문구를 정상화했다.
- 스캐너 탭 라벨을 정리했다:
  - 패턴 신호
  - 조건검색
  - 틱띄기
  - 백테스트
- 패턴 신호 필터 문구를 정리했다:
  - 전체
  - 고점되돌림
  - 이말올
  - 코인/미국/코스피/코스닥
  - 완성 임박만
- 패턴 성과 요약 strip의 표본, 적중률, 평균 수익률 문구를 정상화했다.
- 신호 목록 빈 상태, 로딩, 더 보기 문구를 정상화했다.
- 스캘핑 랭킹 설명과 지표 라벨을 정상화했다.
- 신호 카드의 `구조 점수`, `현재가`, 구분자 문구를 정상화했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

### Signal Detail Text Polish 1

- `/signals/[id]` 화면의 깨진 한글 문구를 정상화했다.
- 신호 상세 헤더, 상태 안내, 차트, 근거, 설명, 성과, 무효화 기준, 액션 버튼 문구를 정리했다.
- `다음 액션` 카드의 수량, 레버리지, 백테스트, 모의 화면, 주문 실패/성공 문구를 정상화했다.
- 성과 패널의 성과 측정 대기 문구를 정상화했다.
- Pattern Score 설명 패널의 위험 필터, 신뢰도, 표본, 적중률, 탐지 근거, 위험 요인, 다음 확인 사항 문구를 정상화했다.
- Explain 피드백 버튼과 부정 사유 문구를 정상화했다.
- 근거 라벨을 피벗, C 목표가, 추세선, 볼린저, 매칭 박스 기준으로 정리했다.
- 관심 등록/해제 버튼 문구를 정상화했다.

Verification:

- Frontend `npm run typecheck`: passed.
- Frontend `npm run build`: passed.

## Purpose

Round 31은 지금까지 구현된 시장 데이터, 신호, 조건검색, 알림, 백테스트, 전략 추적, 청산 스트림, 모의투자 기능을 기준으로 다음 제품 완성도를 올리는 라운드다.

핵심 방향은 새 기능을 무작정 늘리는 것보다, 이미 있는 흐름을 실제 사용자가 반복해서 쓰기 좋은 상태로 다듬는 것이다.

## Current Baseline

- 4시장 종목/캔들/지표: CRYPTO, US, KOSPI, KOSDAQ
- 신호: ABC, TOP, IMALOL, 상태 전이, 신호 상세, Pattern Score, Explain, 피드백
- 조건검색: 즉시 실행, 저장식, 활성/비활성, 스케줄 실행, 매칭 알림, 실행 이력
- 알림: 앱 내 알림, 웹 푸시 전달 시도 기록, 청산 급증 알림, 쿨다운
- 데이터 탭: 파생지표, 펀비차익, TVL, 유통량, 테마/섹터, 청산 피드
- 백테스트: 신호 기반 실행, 수수료, 목표/손절/기간 청산, 워크포워드 검증
- 전략: 저장, 재실행, 성과 히스토리
- 모의투자: 계정, 주문, 체결, 포지션, 성과, 현물/선물, LONG/SHORT, 레버리지, reduce-only 청산
- Admin: 사용자, 알림, 전달 실패, 조건검색 실행 현황 중심 운영 개요

## Recommended Scope

### 1. Paper Trading v2

모의투자는 이미 API와 화면이 있으므로 Round 31의 가장 좋은 확장 후보다.

- 주문 취소 API 추가: `DELETE /api/v1/paper/orders/{id}`
- 선물 포지션 부분청산 UX 개선: 보유 수량 기준으로 빠른 25/50/100% 버튼
- 계정 리셋 전 확인 모달과 이전 run 보관 조회
- 주문 내역 필터: 현물/선물, LONG/SHORT, 청산, 종목
- 포지션 상세: 진입가, 마크가, 증거금, 레버리지, ROE, 실현/미실현 손익
- 수수료/슬리피지 모델 버전 표시
- 신호 상세에서 바로 모의 진입: `신호 보고 Paper Long/Short`

완료 기준:

- `/paper`에서 계정 생성부터 선물 진입, 부분청산, 성과 확인까지 한 화면 흐름이 끊기지 않는다.
- mock과 live API 계약이 동일하다.
- 프론트 `typecheck`, 백엔드 `compileJava`가 통과한다.

### 2. Signal To Action Flow

현재 신호, 백테스트, 모의투자가 각각 존재하지만 연결감이 약하다. 사용자는 신호를 보고 바로 검증하거나 가상 포지션을 열 수 있어야 한다.

- 신호 상세에 액션 바 추가:
  - 모의 Long
  - 모의 Short
  - 백테스트 열기
  - 조건검색식으로 저장
  - 알림 만들기
- Pattern Score, Confidence, 과거 성과, Paper 결과를 한 패널에서 비교
- 같은 종목의 최근 신호/모의 포지션/알림 상태를 종목 상세에 표시
- Risk Guard가 `BLOCK`이면 모의 자동 진입 버튼 비활성화

완료 기준:

- 신호 상세에서 사용자가 최소 3번 이하 클릭으로 모의 포지션을 생성할 수 있다.
- 신호 화면에서 이미 열린 Paper 포지션이 중복 진입 위험으로 표시된다.

### 3. Portfolio Risk For Paper

선물 모의투자가 들어갔기 때문에 위험 관리 화면이 필요하다.

- 계정 단위 노출:
  - 총 평가금액
  - 사용 증거금
  - 가용 현금
  - 포지션 명목가
  - 레버리지 가중 평균
- 종목/시장별 집중도
- 최대 손실 시나리오: -1%, -3%, -5% 가격 충격
- 선물 포지션에 간단한 청산 위험 등급 표시
- 일별 손익 캘린더 또는 7/30일 성과 차트

완료 기준:

- 사용자가 모의 선물 포지션이 계정에 얼마나 위험한지 한눈에 볼 수 있다.
- 실제 청산가를 정확히 약속하지 않고, “모의 위험 등급”으로 표시한다.

### 4. Alert Center v2

조건검색 알림과 청산 급증 알림이 붙었으므로 알림 관리가 더 중요해졌다.

- 알림 설정 통합 화면:
  - 신호 알림
  - 조건검색 저장식 알림
  - 청산 급증 알림
  - 웹 푸시 상태
- 알림별 쿨다운, 활성/비활성, 최근 발생 시각 표시
- 알림 히스토리 필터: unread, signal, scanner, liquidation
- 전달 실패 원인 요약: 권한 없음, 구독 만료, 서버 실패
- 완전 백그라운드 푸시 준비: VAPID/FCM 운영 키 분리 문서화

완료 기준:

- 사용자가 어떤 알림이 왜 왔고, 다음 알림이 언제 가능한지 이해할 수 있다.
- Admin이 실패한 전달을 원인별로 볼 수 있다.

### 5. Scanner Rule Quality

조건검색은 이미 실행과 저장, 알림까지 된다. 다음 단계는 “좋은 조건식”을 찾기 쉽게 만드는 것이다.

- 저장식별 최근 10회 매칭 수 추이
- 조건식 과다매칭/무매칭 경고
- 조건별 기여도 표시: 어떤 조건 때문에 탈락했는지 샘플 제공
- 저장식 복제 기능
- 기본 템플릿:
  - 거래량 급증
  - RSI 과매도 반등
  - MACD 전환
  - MA5/MA20 골든크로스
  - 가격 MA20 상향 돌파

완료 기준:

- 사용자가 처음부터 조건을 모두 만들지 않아도 템플릿으로 시작할 수 있다.
- 저장식이 너무 넓거나 좁은지 화면에서 바로 알 수 있다.

### 6. Admin Operations v2

운영 화면은 기능이 늘어날수록 장애 대응에 직접 필요하다.

- Provider freshness 대시보드 강화
- 스케줄러별 최근 성공/실패, 소요 시간, 다음 실행 예정
- Paper Trading 사용 현황: 활성 계정, 주문 수, 선물 포지션 수
- Scanner rule run 실패 목록
- 청산 스트림 연결 상태와 마지막 이벤트 시각
- 사용자 잠금/해제 이력과 Audit log 검색

완료 기준:

- 운영자가 데이터 지연, 알림 실패, 스캐너 실패, 청산 스트림 끊김을 한 화면에서 확인할 수 있다.

### 7. UI/UX Polish

기능이 많이 붙은 만큼 Round 31에서는 화면 자체의 사용성을 한 번 정리해야 한다. 특히 모바일 기준으로 반복 사용 흐름이 길어지는 부분을 줄인다.

- 하단 탭 정보구조 재점검:
  - 홈
  - 스캐너
  - 모의
  - 데이터
  - 속보
  - 설정
- `/paper` 화면 개선:
  - 계정 요약, 주문, 포지션, 주문내역을 탭 또는 접힘 섹션으로 분리
  - 선물 주문 시 LONG/SHORT, 레버리지, 청산/감소가 한눈에 보이도록 재배치
  - 포지션 카드에 ROE, 증거금, 마크가, 미실현 손익 색상 표시
  - 주문 버튼은 진입/청산 상태에 따라 문구와 색상 분리
- 신호 상세 개선:
  - Pattern Score, Confidence, Risk Guard, 과거 성과를 한 묶음으로 재정렬
  - 주요 액션 버튼을 하단 고정 영역으로 배치
  - 차트와 근거 목록 사이 이동을 줄이는 앵커 또는 탭 적용
- 스캐너 화면 개선:
  - 조건식 작성 영역과 결과 영역을 명확히 분리
  - 저장식 목록은 실행 상태, 최근 매칭 수, 알림 상태가 바로 보이게 정리
  - 조건 추가 UI를 템플릿 중심으로 단순화
- 데이터 화면 개선:
  - 파생, 청산, 펀비차익, TVL, 유통량, 테마 탭의 카드 밀도 통일
  - 숫자 단위, 증감 색상, 정렬 기준을 일관화
  - 실시간/지연/부분 데이터 상태를 상단에 명확히 표시
- 알림/설정 화면 개선:
  - 알림 권한, 웹 푸시, 저장식 알림, 신호 알림을 한 흐름으로 정리
  - 전달 실패나 권한 없음 상태는 사용자가 바로 조치할 수 있게 표시
- 공통 컴포넌트 정리:
  - Loading, Empty, Error, Partial, Stale 상태 문구 통일
  - 카드 안 카드 중첩 제거
  - 버튼/탭/필터 높이와 여백 통일
  - 긴 종목명, 긴 숫자, 긴 알림 문구의 모바일 줄바꿈 점검
  - 주요 숫자는 `tabular-nums` 기준으로 정렬

완료 기준:

- 주요 화면이 모바일 360px 폭에서도 텍스트 겹침 없이 동작한다.
- 사용자가 신호 확인, 모의 주문, 조건검색 실행, 알림 확인을 화면 이동 최소화로 끝낼 수 있다.
- Empty/Error/Stale 상태가 화면마다 다른 말투로 보이지 않는다.

## Nice-To-Have Improvements

- 종목 검색에 최근 사용 종목 표시
- 백테스트 결과에서 바로 전략 저장 이름 자동 추천
- 신호 성과 요약에 표본 부족 경고를 더 선명하게 표시
- 데이터 탭의 파생/청산 정보를 종목 상세에도 압축 표시
- 모든 주요 화면에 Empty/Partial/Stale 상태 문구 정리
- API 계약 문서의 깨진 인코딩 정리

## Suggested Round 31 Cut

현실적인 Round 31 범위는 아래 3개로 자르는 것이 좋다.

1. Paper Trading v2
2. Signal To Action Flow
3. Alert Center v2 일부

이 조합이 좋은 이유:

- 이미 있는 구현 위에 얹는 개선이라 리스크가 낮다.
- 사용자가 실제로 반복 사용할 핵심 흐름이 좋아진다.
- 선물 모의투자 기능을 단순 “있음”이 아니라 “쓸 만함”으로 끌어올린다.

## Deferred

- 실제 거래소 주문 API
- 실자금 연동
- 자동매매
- 정확한 선물 청산가 계산
- 복잡한 포트폴리오 VaR/상관관계 모델

이 항목들은 컴플라이언스와 운영 리스크가 커서 Paper Trading과 검증 기능이 더 안정화된 뒤 별도 라운드로 분리한다.

## Verification Plan

- Backend: `.\gradlew.bat compileJava`
- Frontend: `npm run typecheck`
- Frontend production build: `npm run build`
- Mock/live 계약 비교: paper, scanner, notifications
- Smoke path:
  - login
  - signal detail
  - paper account create
  - futures open
  - futures partial close
  - alert settings
  - admin overview

## 진행 기록 - Data Page Text Polish 1

- `/data` 화면 전체의 깨진 한글 UI 문구를 재정리했다.
- TVL, 유통량, 테마/섹터, 펀비차익, 파생, 청산 탭의 필터/정렬/빈 상태/요약 문구를 통일했다.
- 유통률, TVL, FDV, 미결제약정, 청산 금액 등 숫자 표시에서 비정상 값이 들어와도 `-`로 방어하도록 보강했다.
- 롱/숏 비율 바, 펀딩비, 청산 급증 상태, LIVE/연결 대기 상태를 사용자가 바로 읽을 수 있는 문구로 정리했다.
- 검증: `npm run typecheck` 통과.

## 진행 기록 - News UI Text Polish 1

- `/news` 목록 화면의 출처 필터, 제목, 빈 상태, 더 보기 버튼 문구를 정상 한글로 정리했다.
- `/news/[id]` 상세 화면의 뒤로가기 접근성 라벨, 제목, 빈 상태, 내용 없음, 원문 보기 문구를 정리했다.
- 뉴스 출처 라벨은 공통 `newsSourceLabel()` 기준으로 목록/상세가 동일하게 `코인니스`, `Bloomberg`를 표시하도록 맞췄다.
- 검증: `npm run typecheck` 통과.

## 진행 기록 - Detail Pages Text Polish 1

- `/derivatives/[symbol]` 파생 상세 화면을 정리했다: 뒤로가기, 제목, 마크 가격, 펀딩비, 미결제약정, 다음 펀딩, 롱/숏 비율 추이, OI 추이 문구를 정상화했다.
- `/scalp/[symbol]` 스캘핑 상세 화면을 정리했다: 벽 상태, 스캘핑 점수, 체결 흐름, 매수/매도 비율, 호가 상위 레벨, 벽 취소 위험 문구를 정상화했다.
- `/themes/[id]` 테마 상세 화면을 정리했다: 구성종목, 신뢰도 배지, 출처, 빈 상태 설명을 정상화했다.
- `/tvl/[id]` TVL 상세 화면을 정리했다: TVL 히스토리, 빈 히스토리 상태, 차트 제목 영역을 정상화했다.
- 검증: `npm run typecheck` 통과.
