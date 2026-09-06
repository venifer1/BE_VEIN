# VEIN — 수익화 설계

> **기준일:** 2026-09-06
> **목표:** 부수입 수준 (월 100~500만 원)
> **전략:** ① 도구 티어 과금 + ② 코인 신호 유료화
> **전제:** `PROJECT_STATUS.md` 0장 — 이 제품의 1순위 목적은 여전히 "내가 쓰려고"다.
> 수익화가 그 목적을 훼손하면 수익화를 접는다.

---

## 1. 먼저 — 목표 금액을 역산하면 무슨 일이 벌어지나

이걸 먼저 보지 않으면 나머지 설계가 무의미하다.

| 가격 | 필요 유료회원 (월 300만 원 기준) | 필요 MAU (전환율 3~5% 가정) |
|---|---|---|
| 월 9,900원 | 약 300명 | **6,000 ~ 10,000명** |
| 월 19,900원 | 약 150명 | **3,000 ~ 5,000명** |
| 월 49,900원 | 약 60명 | **1,200 ~ 2,000명** |

### 여기서 나오는 두 가지 결론

**① 부수입 목표는 자동으로 "공개 서비스"를 의미한다.**
초대제 무료(A안)로는 이 숫자에 절대 도달하지 못한다. 즉 유료화를 결정하는 순간
**규제와 데이터 라이선스가 전면 발동**한다. "조용히 몇 명한테만 팔기"는 성립하지 않는다.

**② 저가 대량보다 고가 소수가 현실적이다.**
필요 MAU가 5배 차이 난다. 그리고 고가 티어는 **진지한 트레이더**를 대상으로 하는데,
그게 정확히 도구 레이어(백테스트·조건검색 자동화·전략 검증)를 원하는 층이다.
전략과 가격이 같은 방향을 가리킨다.

> **권장 출발점: 월 19,900원 단일 PRO.**
> 49,900원은 검증 안 된 상태에서 너무 공격적이고, 9,900원은 필요 MAU가 비현실적이다.
> 연 결제(199,000원, 2개월분 할인)를 같이 열어 현금흐름을 앞당긴다.

### 1.4 대안 검토 — 무료로 풀고 후원을 받으면 안 되나?

**법적으로는 훨씬 깨끗하다. 하지만 목표 금액과는 맞지 않는다.** 둘 다 사실이다.

#### 🟢 법적 이점

- 유사투자자문업 정의가 **"일정한 대가를 받고"** 를 요건으로 한다. 후원이 **반대급부 없는
  자발적 지급**이면 대가성이 부정될 여지가 크다 → 자문 규제 자체가 발동하지 않는다
- **통신판매업 신고 불필요** (판매가 아님)
- 청약철회·환불 정책·약관규제법 부담이 크게 줄어든다

#### 🔴 결정적 조건 — 후원자에게 아무것도 더 주면 안 된다

이걸 어기는 순간 위 이점이 전부 사라진다.

| | 안전 | 위험 |
|---|---|---|
| 형태 | GitHub Sponsors · Buy Me a Coffee 스타일 | **Patreon식 티어별 혜택** |
| 대가 | 없음. 이름 표시 정도 | **후원자 전용 기능·신호·알림 주기** |
| 판정 | 순수 후원 | **실질적 유료 구독** → 규제 발동 |

즉 **"후원하면 PRO 기능"은 이름만 후원이지 법적으로는 유료화다.** 후원 모델을 택하려면
기능 차등을 **완전히 포기**해야 한다. 4~5장의 티어 설계와 양립하지 않는다.

#### 🟡 세금은 여전히 낸다

"후원금이라 세금이 없다"는 오해가 많다. 반복적·계속적으로 받는 후원은 증여가 아니라
**사업소득 또는 기타소득**으로 본다(국세청은 유튜브 슈퍼챗 등을 이 기준으로 판단해 왔다).
일정 규모를 넘으면 **사업자등록**도 필요하다.

#### 🔴 데이터 라이선스는 오히려 나빠질 수 있다

무료 서비스라도 외부 API 약관의 재배포 제한에는 걸린다. 그런데 **후원을 받기 시작하면
"상업적 이용"으로 인정될 소지가 커진다.** 유료화 대비 이 부분은 나아지지 않고, 경우에
따라 애매해진다. 7장 데이터 라이선스 체크리스트는 후원 모델에서도 그대로 유효하다.

#### 🔴 수익 규모 — 목표와 자릿수가 다르다

한국은 개인 개발자·오픈소스 후원 문화가 약하다. 개인 프로젝트 후원은 대개
**월 수만 ~ 수십만 원** 수준에서 형성된다. 월 100~500만 원은 사용자가 수만 명이거나
강력한 커뮤니티가 있을 때 이야기다. **부수입 목표로는 사실상 달성 불가하다.**

#### ✅ 그럼에도 — 후원은 "검증 도구"로 최적이다

여기가 진짜 쓸모다. 9장 1단계(무료 공개 베타)에 **후원 버튼을 붙이면
규제·결제·약관 부담 없이 "사람들이 이 도구에 돈을 낼 의사가 있는가"를 측정**할 수 있다.

- 보는 지표는 **후원 금액이 아니라 후원자 비율**이다
- MAU 대비 후원자 비율이 **0.5%를 넘으면** 유료 전환 시 3~5% 전환율을 기대할 근거가 된다
- 0.1% 미만이면 **유료화를 하지 않는 게 맞다** — 규제·라이선스 비용을 회수 못 한다

수단: Buy Me a Coffee · 토스 후원 · 카카오페이 송금 링크 (기능 차등 없이 단순 링크만)

#### 정리

| 모델 | 법적 부담 | 예상 수익 | 목표 적합도 |
|---|---|---|---|
| 무료 + 후원 단독 | **낮음** | 월 수만~수십만 원 | ❌ 부수입 목표 미달 |
| 무료 + 후원 → 이후 PRO 전환 | 낮음 → 높음 | 단계적 | ✅ **권장 경로** |
| 처음부터 PRO 유료 | 높음 | 월 100~500만 원 가능 | ⚠️ 검증 없이 비용 선지출 |

> **결론: 후원은 유료화의 대체재가 아니라 전 단계다.**
> 9장 1단계에 후원을 붙이고, 후원자 비율이 근거를 만들어주면 그때 PRO로 간다.
> 만약 "돈 버는 것보다 규제 안 걸리는 게 더 중요하다"로 목표가 바뀐다면,
> 후원 단독으로 끝내는 것도 완결된 선택이다 — 그때는 4~5장 티어 설계를 폐기한다.

---

## 2. 티어 설계 — 가르는 기준은 "조언 vs 도구"

법적 안전지대를 제품 구조에 그대로 박아 넣는다.

### 원칙

```
패턴 신호(조언)      → 무료. 전부 연다.
자동화·검증(도구)    → 유료. 여기서 돈을 받는다.
```

**왜 신호를 무료로 주는가 — 두 가지 이유가 겹친다.**

1. **법적**: "대가를 받고 투자판단에 관한 조언을 제공"하는 구조를 만들지 않는다.
   유료 대상은 어디까지나 사용자가 자기 가설을 돌리는 **계산·자동화 기능**이다.
2. **상업적**: 신호는 봐도 **언제 뜨는지 모르면 못 쓴다.** 실질 가치는 알림 주기와
   자동 평가에 있다. 신호를 열어도 PRO 매력이 줄지 않는다 — 오히려 유입 경로가 된다.

### 티어표

| 기능 | FREE | PRO (월 19,900원) |
|---|---|---|
| **— 조언 레이어 (전부 무료) —** | | |
| 4시장 시세·캔들·차트·지표 | ✅ | ✅ |
| 패턴 신호 열람 (ABC·TOP·IMALOL) | ✅ **전체** | ✅ 전체 |
| Pattern Score · Explain · Risk Guard | ✅ | ✅ |
| 속보 (Bloomberg·텔레그램) | ✅ | ✅ |
| 데이터 탭 (TVL·유통량·테마·펀비차익) | ✅ | ✅ |
| **— 도구 레이어 (과금 대상) —** | | |
| 관심종목 | 20종목 | 무제한 |
| 알림 규칙 | 3개 · 쿨다운 최소 **60분** | 무제한 · 쿨다운 최소 **5분** |
| 백그라운드 웹푸시 | ✖ | ✅ |
| 조건검색 즉시 실행 | 일 5회 | 무제한 |
| 조건검색 저장식 | 2개 · **일 1회** 자동평가 | 무제한 · **15분 주기** 자동평가 + 매칭 알림 |
| 백테스트 | 월 10회 · 결과 7일 보관 | 무제한 · 영구 보관 |
| 워크포워드 IS/OOS 과적합 검증 | ✖ | ✅ |
| 전략 저장·재실행·성과 히스토리 | 1개 | 무제한 |
| 모의투자 | 현물 계좌 1개 | 다계좌 · **선물/레버리지/부분청산** |
| 신호 성과 히스토리 | 최근 7일 | 전체 + 패턴별 적중률 요약 |
| 강제청산 스트림 | 지연 스냅샷 | **실시간 + 급증 알림** |
| 틱띄기 스캘핑 | ✖ | ✅ |
| 데이터 내보내기 (CSV) | ✖ | ✅ |

### 이 표의 방어 논리

PRO 항목을 다시 읽어보면 **하나도 "무슨 종목을 사라"가 없다.** 전부
*더 자주 평가하고 · 더 많이 저장하고 · 더 길게 보관하고 · 더 정교하게 검증하는* 것이다.
판단 주체는 끝까지 사용자다. 이게 유사투자자문업 규제를 피하는 핵심 구조다.

---

## 3. 코인/주식 분리 — 2차 방어선

만약 법률 검토 결과 위 구조만으로 부족하다는 판단이 나오면, **주식(US·KOSPI·KOSDAQ)을
유료 범위에서 제외**하는 2차 방어선을 쓴다.

- 가상자산은 자본시장법상 **금융투자상품이 아니다** → 자문 규제 대상 밖
- 기획서 26.1의 "MVP 핵심 시장은 코인"과도 정합적
- 실무적으로는 PRO 기능 중 **코인 마켓에만 적용**하고 주식은 FREE 수준으로 고정

> ⚠️ **회색지대다.** 코인 리딩방 단속이 강화되는 흐름이고 입법 논의도 진행 중이다.
> 이 경로를 쓰려면 **변호사 확인 + 금감원 비조치의견서**가 사실상 필수다.

**부수적 이점**: 주식을 유료 범위에서 빼면 **yfinance / pykrx 데이터 라이선스 문제도
같이 줄어든다** (상업적 재배포 이슈의 대부분이 주식 소스에서 나온다).

---

## 4. DB 설계 — V23

`users.role`은 **권한**(TESTER/SUPER_ADMIN)이고 **과금 플랜과 다른 축**이다.
role에 PRO를 끼워 넣지 않는다 — 관리자가 무료 사용자일 수도, 유료 사용자일 수도 있다.

```sql
-- V23__billing.sql (초안)

-- 1) 현재 플랜은 users 에 비정규화 (조회가 모든 요청마다 일어남)
ALTER TABLE users ADD COLUMN plan VARCHAR(16) NOT NULL DEFAULT 'FREE';  -- FREE | PRO
ALTER TABLE users ADD COLUMN plan_expires_at TIMESTAMPTZ;               -- NULL = 무기한(FREE)

-- 2) 구독 이력 (원장 — 절대 UPDATE 하지 않고 append)
CREATE TABLE subscriptions (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan            VARCHAR(16) NOT NULL,
  status          VARCHAR(16) NOT NULL,   -- ACTIVE|CANCELED|EXPIRED|PAST_DUE|REFUNDED
  period          VARCHAR(8)  NOT NULL,   -- MONTHLY|YEARLY
  amount          NUMERIC(12,2) NOT NULL,
  currency        VARCHAR(8)  NOT NULL DEFAULT 'KRW',
  pg_provider     VARCHAR(24),            -- TOSS|PORTONE ...
  pg_billing_key  VARCHAR(128),           -- 정기결제 빌링키 (원문 저장 금지, 참조만)
  pg_order_id     VARCHAR(64),
  started_at      TIMESTAMPTZ NOT NULL,
  current_period_end TIMESTAMPTZ NOT NULL,
  canceled_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_subs_user_status ON subscriptions(user_id, status);

-- 3) 사용량 카운터 (일/월 단위 리셋되는 미터링)
CREATE TABLE usage_counters (
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  metric      VARCHAR(32) NOT NULL,   -- SCANNER_RUN|BACKTEST_RUN|...
  bucket      DATE        NOT NULL,   -- 일 단위 버킷 (월 지표는 월초 날짜)
  count       INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, metric, bucket)
);

-- 4) 결제 이벤트 로그 (PG 웹훅 멱등 처리용)
CREATE TABLE payment_events (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  provider    VARCHAR(24) NOT NULL,
  event_id    VARCHAR(128) NOT NULL,   -- PG 측 고유 ID
  event_type  VARCHAR(48) NOT NULL,
  payload     JSONB NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, event_id)          -- 멱등: 같은 웹훅 두 번 와도 1건
);
```

**설계 노트**
- `users.plan`은 캐시다. **진실은 `subscriptions`** — 불일치 시 배치로 정정한다.
- 결제 실패 시 즉시 강등하지 않고 `PAST_DUE`로 두고 **유예기간(3~7일)** 을 준다
  (전자상거래법·소비자 보호 관점에서도 안전).
- `usage_counters`는 Redis로 대체하지 않는다 — 과금 근거라 **영속 저장**이 맞다.

---

## 5. 게이팅 구현 지점

서비스 메서드가 이미 전부 `Long userId`를 받고 있어 삽입 지점이 깨끗하다.

### 새로 만들 것

```
com.vein.billing/
  Plan.java                 enum FREE, PRO
  PlanLimits.java           플랜별 한도 상수 (application.yml 바인딩)
  PlanService.java          currentPlan(userId), require(userId, Feature)
  UsageService.java         increment(userId, metric), checkQuota(userId, metric)
  SubscriptionService.java  구독 생성·갱신·취소·만료 배치
  BillingWebhookController  PG 웹훅 수신 (멱등)
```

`ErrorCode`에 추가:
```java
PRO_REQUIRED,          // 403 — 플랜 미달
PLAN_LIMIT_EXCEEDED,   // 429 — 한도 초과 (meta 에 limit/used/reset_at)
```

### 삽입 위치 (파일:메서드)

| 대상 | 위치 | 검사 |
|---|---|---|
| 조건검색 즉시 실행 | `ConditionScannerService.run()` | ⚠️ **현재 `userId`를 안 받는다 — 시그니처 추가 필요** + 일 5회 쿼터 |
| 조건검색 저장식 | `ConditionScannerService.save()` | 저장 개수 한도 |
| 저장식 자동평가 주기 | `ConditionScannerScheduler` | 플랜별 주기 분기 (FREE 일 1회 / PRO 15분) |
| 백테스트 | `BacktestService.run()` | 월 10회 쿼터 + 워크포워드 옵션 PRO 전용 |
| 전략 | `StrategyService.create()` | 저장 개수 한도 |
| 알림 규칙 | `AlertService.create()` | 개수 한도 + `cooldownSec` 하한 검증 |
| 웹푸시 구독 | `WebPushService.subscribe()` | PRO 전용 |
| 모의투자 | `PaperTradingService.createAccount()` | 계좌 수 한도 |
| 모의투자 선물 | `PaperTradingService.createOrder()` | `investmentType=FUTURES` PRO 전용 |
| 신호 성과 | `SignalService` 성과 조회 | FREE는 7일로 기간 절단 |
| 틱띄기 | `ScalpController` | PRO 전용 |
| 청산 실시간 | `LiquidationController` | FREE는 지연 스냅샷 |

### 관리자 / 내부 계정은 모든 한도를 우회한다

운영자(=나)는 **모든 기능을 제한 없이** 쓸 수 있어야 한다. 내가 못 쓰는 도구를 파는 건
말이 안 되고, 장애 대응·검증도 불가능하다.

플랜 enum에 **`UNLIMITED`** 를 추가하고, `PlanService`가 단일 지점에서 판정한다:

```java
public Plan currentPlan(Long userId) {
    User u = userRepository.findById(userId).orElseThrow(...);
    // 1) SUPER_ADMIN 은 무조건 무제한 — 결제 상태와 무관
    if ("SUPER_ADMIN".equals(u.getRole())) return Plan.UNLIMITED;
    // 2) 명시적으로 부여된 내부/평생 계정
    if (u.getPlan() == Plan.UNLIMITED) return Plan.UNLIMITED;
    // 3) 만료된 유료 플랜은 FREE 로 강등
    if (u.getPlan() == Plan.PRO && isExpired(u.getPlanExpiresAt())) return Plan.FREE;
    return u.getPlan();
}
```

그리고 **쿼터 계산 자체를 건너뛴다** — 카운터도 올리지 않는다:

```java
public void checkQuota(Long userId, Metric metric) {
    if (planService.currentPlan(userId) == Plan.UNLIMITED) return;  // 조기 반환
    ...
}
```

**설계 규칙**

- `UNLIMITED`는 **`PRO`의 상위**다. PRO 전용 기능 검사도 통과한다
  (`plan.atLeast(Plan.PRO)` 형태로 비교 — enum ordinal 순서를 `FREE < PRO < UNLIMITED`로)
- **결제 상태와 완전히 무관**하다. 구독 만료 배치가 `SUPER_ADMIN`을 강등시키지 않도록
  만료 처리 쿼리에서 제외할 것
- `UNLIMITED` 부여/회수는 **`audit_logs`에 남긴다** — 나중에 지인에게 평생 계정을 주게 되면
  그 이력이 필요하다 (이미 `AuditService`가 있다)
- Admin 화면(`/admin/users`)에 **플랜 수동 변경** 기능을 붙인다. 베타 테스터에게
  PRO를 임시 부여하거나, 환불·장애 보상 시 기간을 연장하는 데 반드시 필요하다

```sql
-- V23 에 포함
-- plan: FREE | PRO | UNLIMITED
-- 기존 SUPER_ADMIN 계정은 즉시 UNLIMITED 로
UPDATE users SET plan = 'UNLIMITED' WHERE role = 'SUPER_ADMIN';
```

> ⚠️ **주의**: `UNLIMITED`를 남발하면 유료 전환 데이터가 오염된다. 지인·테스터에게 주는
> 계정은 `PRO` + `plan_expires_at` 만료일을 두고, `UNLIMITED`는 운영자 본인으로 제한한다.

### 구현 방식

한도 상수는 **코드에 하드코딩하지 않는다** (기획서 23.2 개발 원칙과 동일).
`application.yml`의 `vein.plan.*`에 두고 `@ConfigurationProperties`로 바인딩한다.

```yaml
vein:
  plan:
    free:
      watchlist-max: 20
      alert-max: 3
      alert-min-cooldown-sec: 3600
      scanner-run-daily: 5
      scanner-rule-max: 2
      scanner-eval-interval-min: 1440
      backtest-monthly: 10
      backtest-retention-days: 7
      strategy-max: 1
      paper-account-max: 1
    pro:
      watchlist-max: -1          # -1 = 무제한
      alert-max: -1
      alert-min-cooldown-sec: 300
      scanner-run-daily: -1
      scanner-rule-max: -1
      scanner-eval-interval-min: 15
      backtest-monthly: -1
      backtest-retention-days: -1
      strategy-max: -1
      paper-account-max: 5
```

### 프론트

- `GET /me` 응답에 `plan`, `plan_expires_at`, `limits` 를 실어 보낸다 (라운드트립 절약)
- 한도 초과는 **막기 전에 보여준다** — "저장식 2/2 사용 중" 같은 잔량 표시가
  결제 전환에 결정적이고, 갑자기 막히는 UX는 환불 요청을 부른다
- PRO 전용 기능은 **숨기지 말고 잠금 상태로 노출** (가치를 알아야 산다)

---

## 6. 결제 연동

| 항목 | 선택 |
|---|---|
| PG | **토스페이먼츠** 또는 **포트원(구 아임포트)** — 개인사업자 가입 가능, 정기결제(빌링키) 지원 |
| 방식 | 빌링키 기반 정기결제. 카드 정보는 **절대 자체 저장하지 않는다** (PG 토큰만) |
| 웹훅 | `payment_events` 테이블로 멱등 처리. 같은 이벤트 재수신해도 1회만 반영 |
| 실패 처리 | `PAST_DUE` → 유예 3~7일 → 재시도 → 실패 시 `EXPIRED` 강등 |
| 환불 | 디지털 콘텐츠 청약철회 예외를 **약관에 명시**하되, 미사용 기간 일할 환불을 정책으로 둔다 |

---

## 7. 유료화 전 법적 체크리스트

**착수 전 반드시 (순서대로)**

- [ ] **금융규제 전문 변호사 상담** — 위 티어 설계가 유사투자자문업 해당 여부
- [ ] **금감원 비조치의견서** 신청 검토 — 서비스 설계를 제출해 "규제 대상 아님" 확인
- [ ] **데이터 라이선스 정리**
  - [ ] KRX 정보이용료 계약 (또는 주식을 유료 범위에서 제외)
  - [ ] 미국주식 유료 벤더 전환 (yfinance는 상업적 사용 불가)
  - [ ] Bloomberg RSS 재배포 조건 확인
  - [ ] 텔레그램 채널 콘텐츠 사용 권리 확인 (또는 제외)
  - [ ] 코인 API 상업적 이용 약관 재확인 (Upbit·Binance·CoinGecko·DefiLlama·Bybit)
- [ ] **통신판매업 신고** (관할 구청)
- [ ] **이용약관 · 개인정보처리방침** 작성·공개
- [ ] **정보통신망법** — 마케팅 푸시 수신동의 분리, 야간(21~08시) 별도 동의
      → **신호 알림과 마케팅 알림을 코드 레벨에서 분리**할 것
- [ ] **표시광고법** — 백테스트 수익률을 광고에 쓰지 않는다. 과거 성과 광고는 단속 대상
- [ ] **이해상충 정책 명문화** — 본인 매매와 신호 배포의 시차·공시

---

## 8. 하지 말 것

- ❌ **"수익률 XX%" 마케팅** — 표시광고법 직격. 백테스트 결과는 제품 안에서만 보여준다
- ❌ **1:1 상담·오픈채팅·텔레그램 유료방** — 양방향 유료는 투자자문업 등록 대상
- ❌ **"추천 종목" 표현** — 코드·UI 문구에서 금지어로 관리. "탐지된 후보"로 통일
- ❌ **자동매매·거래소 API 키 연동** — 컴플라이언스 영구 보류 항목
- ❌ **PRO 전용 개인 맞춤 신호** — 개별성이 생기는 순간 방어 논리가 무너진다
- ❌ 카드정보 자체 저장

---

## 9. 실행 순서

수익화는 **Track A(내가 쓰기 위해)가 끝난 뒤** 착수한다. 순서를 지키지 않으면
검증도 안 된 제품에 규제 비용을 먼저 지불하게 된다.

```
0. [현재] 무료·초대제로 내가 쓴다              ← PROJECT_STATUS Track A
        ↓ "데스크톱 터미널을 2주 이상 안 켰다"
1. 무료 공개 베타 + 후원 버튼                  ← 1.4장
   가입 개방, 사용 데이터 수집, 지불의사 측정
        ↓ ① 기능별 사용률  ② 후원자 비율 ≥ 0.5%
        ↓ (0.1% 미만이면 여기서 멈춘다 — 유료화하지 않는다)
2. 법률 검토 + 데이터 라이선스 정리            ← 7장 체크리스트
        ↓
3. V23 + 게이팅 구현 (결제 없이 플랜만)        ← 4~5장
        ↓ FREE 한도를 걸고 이탈 반응 관찰
4. 결제 연동 · 통신판매업 신고 · 약관           ← 6장
        ↓
5. PRO 오픈 (후원 버튼은 유지해도 무방)
```

**1단계의 핵심 질문 두 가지**

1. *PRO에 넣으려는 기능(백테스트·조건검색 자동화·전략)을 무료 사용자들이 실제로 쓰는가?*
   안 쓴다면 티어표를 다시 짜야 한다. **쓰는 사람이 없는 기능에 값을 매길 수는 없다.**
2. *돈을 낼 의사가 있는가?* — 후원자 비율로 측정한다. 이 답이 안 나오면 2단계
   (변호사비·데이터 라이선스)로 넘어가지 않는다. **비용을 먼저 쓰지 않는 게 이 순서의 핵심이다.**

---

## 10. 냉정한 전제

- 필요 MAU 3,000~5,000은 **개인 개발자에게 작은 숫자가 아니다.** 마케팅 없이는 안 된다
- 데이터 라이선스 비용이 수익을 잠식할 수 있다 — **3단계 전에 비용을 먼저 계산할 것**
- 유사투자자문업 이미지("리딩방")가 붙으면 브랜드가 손상된다. 도구 포지셔닝을 끝까지 지킬 것
- **이 전부가 "내가 쓰려고 만든다"는 제1 전제와 충돌하기 시작하면, 수익화를 접는 것도 정답이다**

---

*관련 문서: `PROJECT_STATUS.md`(전체 현황·우선순위) · `API_CONTRACT.md`(API 계약)*
