# Entity 설계

## 1. 엔터티 설계 원칙

1) 기준 상태와 이력 상태를 분리

Order, PortfolioPosition, CashBalance 같은 것은 현재 상태

OrderEvent, Execution, RiskCheckResult, PositionLotHistory 같은 것은 이력/증적

2) 주문 의도와 실제 주문을 분리

OrderIntent: 사람이든 전략이든 리밸런싱이든 공통 입력

Order: OMS가 실제로 상태 머신으로 관리하는 객체

3) Execution과 OrderEvent를 분리

Execution: 체결 비즈니스 사실

OrderEvent: 상태 전이 및 전체 이벤트 로그

예를 들어 ACK, CANCEL_REQUESTED, REJECT는 체결이 아니지만 이벤트는 맞다. 

반대로 PARTIAL_FILL은 이벤트이면서 체결 레코드를 동반한다.

4) 리스크 판정과 리스크 규칙 정의를 분리

RiskRule: 설정값

RiskCheckResult: 특정 Intent/Order에 대해 실제 검사한 결과

운영자가 규칙을 바꾸고 위반 주문을 조회하는 시나리오와 잘 맞는다.

## 2. 도메인 그룹

MVP 기준으로 엔터티를 6개 그룹으로 나눈다.

### A. 기준정보 / 시장데이터
Instrument

MarketPriceSnapshot

TradingSession 또는 ExchangeCalendar(간단 버전)

### B. 포트폴리오 / 계좌
Portfolio

PortfolioPosition

CashBalance

### C. 주문 생성
OrderIntent

RebalancePlan

StrategySignal(선택)

ManualOrderRequest(API DTO 성격이면 엔터티까지는 불필요)

### D. 리스크
RiskRule

RiskCheckResult

### E. OMS / 체결
Order

OrderEvent

Execution

CancelRequest(분리 여부 선택)

IdempotencyRecord(중복 방지용)

### F. 사후처리 / 감사
PortfolioLedger

PnlSnapshot

AuditLog

ReplayCursor 또는 ReplayJob(확장)

## 3. Entity 상세 설계

### 3-1. Instrument

종목 기준정보

역할:

- 자산 유형, 거래소, 호가단위, 매매단위 관리
- 주문 유효성 검사와 execution simulator의 가격 처리 기준

| 컬럼명        | 타입            | 제약           | 설명                                        |
| ---------- | ------------- | ------------ | ----------------------------------------- |
| id         | bigint        | PK           | 종목 ID                                     |
| symbol     | varchar(32)   | UK, NOT NULL | 종목 코드                                     |
| name       | varchar(128)  | NOT NULL     | 종목명                                       |
| asset_type | varchar(32)   | NOT NULL     | 자산 유형 (`EQUITY`, `ETF`, `BOND`, `CRYPTO`) |
| exchange   | varchar(32)   | NOT NULL     | 거래소                                       |
| currency   | varchar(16)   | NOT NULL     | 통화                                        |
| tick_size  | decimal(18,8) | NOT NULL     | 호가 단위                                     |
| lot_size   | decimal(18,8) | NOT NULL     | 주문 단위                                     |
| is_active  | boolean       | NOT NULL     | 활성 여부                                     |
| created_at | timestamp     | NOT NULL     | 생성 시각                                     |
| updated_at | timestamp     | NOT NULL     | 수정 시각                                     |

### 3-2. market_price_snapshot
시장 가격 스냅샷 테이블

역할
- 리밸런싱 계산, 가격 밴드 체크, 평가손익 계산에 사용
- MVP에서는 최신가 위주로 관리 가능

| 컬럼명             | 타입            | 제약       | 설명       |
| --------------- | ------------- | -------- | -------- |
| id              | bigint        | PK       | 스냅샷 ID   |
| instrument_id   | bigint        | FK       | 종목 ID    |
| market_price    | decimal(18,8) | NOT NULL | 현재가      |
| bid_price       | decimal(18,8) | NULL     | 매수호가     |
| ask_price       | decimal(18,8) | NULL     | 매도호가     |
| price_timestamp | timestamp     | NOT NULL | 가격 기준 시각 |
| created_at      | timestamp     | NOT NULL | 저장 시각    |

관계
- instrument (1) : (N) market_price_snapshot
계
### 3-3. Portfolio

계좌/운용 단위 최상위 aggregate

역할
- 포지션, 현금, 주문의 소유 주체
- PM 또는 전략이 주문을 발생시키는 논리 단위

| 컬럼명            | 타입           | 제약           | 설명                        |
| -------------- | ------------ | ------------ | ------------------------- |
| id             | bigint       | PK           | 포트폴리오 ID                  |
| portfolio_code | varchar(64)  | UK, NOT NULL | 포트폴리오 코드                  |
| name           | varchar(128) | NOT NULL     | 포트폴리오명                    |
| base_currency  | varchar(16)  | NOT NULL     | 기준 통화                     |
| status         | varchar(32)  | NOT NULL     | 상태 (`ACTIVE`, `INACTIVE`) |
| created_at     | timestamp    | NOT NULL     | 생성 시각                     |
| updated_at     | timestamp    | NOT NULL     | 수정 시각                     |

관
- 1:N `PortfolioPosition`
- 1:1 또는 1:N `CashBalance`
- 1:N `OrderIntent`
- 1:N `Order`

### 3-4. PortfolioPosition

현재 보유 포지션 상태

역할
- 종목별 보유 수량
- 평균단가
- 평가손익 계산의 기준

| 컬럼명           | 타입            | 제약           | 설명       |
| ------------- | ------------- | ------------ | -------- |
| id            | bigint        | PK           | 포지션 ID   |
| portfolio_id  | bigint        | FK, NOT NULL | 포트폴리오 ID |
| instrument_id | bigint        | FK, NOT NULL | 종목 ID    |
| quantity      | decimal(18,8) | NOT NULL     | 보유 수량    |
| avg_price     | decimal(18,8) | NOT NULL     | 평균단가     |
| book_cost     | decimal(18,8) | NOT NULL     | 총 취득원가   |
| realized_pnl  | decimal(18,8) | NOT NULL     | 누적 실현손익  |
| version       | bigint        | NOT NULL     | 낙관적 락 버전 |
| updated_at    | timestamp     | NOT NULL     | 수정 시각    |

제약
- `(portfolio_id, instrument_id)` unique

관계
- portfolio (1) : (N) portfolio_position
- instrument (1) : (N) portfolio_position

### 3-5. CashBalance

현금 잔고

역할
- 주문 가능 현금, 예약 현금, 정산 현금 관리
- 체결 후 현금 증감 반영

| 컬럼명            | 타입            | 제약           | 설명       |
| -------------- | ------------- | ------------ | -------- |
| id             | bigint        | PK           | 현금 잔고 ID |
| portfolio_id   | bigint        | FK, NOT NULL | 포트폴리오 ID |
| currency       | varchar(16)   | NOT NULL     | 통화       |
| available_cash | decimal(18,8) | NOT NULL     | 주문 가능 현금 |
| reserved_cash  | decimal(18,8) | NOT NULL     | 주문 예약 현금 |
| settled_cash   | decimal(18,8) | NOT NULL     | 정산 완료 현금 |
| version        | bigint        | NOT NULL     | 낙관적 락 버전 |
| updated_at     | timestamp     | NOT NULL     | 수정 시각    |

제약
- unique (portfolio_id, currency)

관계
- portfolio (1) : (N) cash_balance

### 3-6. OrderIntent

주문 의도의 공통 표현. 이 프로젝트의 앞단 핵심 엔터티

역할
- 수동 입력, 리밸런싱, 전략 신호를 공통 구조로 수렴
- 아직 시장 주문은 아님
- 리스크 레이어의 입력 단위

| 컬럼명             | 타입            | 제약           | 설명                                                                     |
| --------------- | ------------- | ------------ | ---------------------------------------------------------------------- |
| id              | bigint        | PK           | 주문 의도 ID                                                               |
| portfolio_id    | bigint        | FK, NOT NULL | 포트폴리오 ID                                                               |
| instrument_id   | bigint        | FK, NOT NULL | 종목 ID                                                                  |
| source_type     | varchar(32)   | NOT NULL     | 생성 원천 (`MANUAL`, `REBALANCING`, `STRATEGY`)                            |
| source_ref_id   | varchar(128)  | NULL         | 원천 참조 ID                                                               |
| side            | varchar(16)   | NOT NULL     | 매수/매도 (`BUY`, `SELL`)                                                  |
| order_type      | varchar(16)   | NOT NULL     | 주문 유형 (`MARKET`, `LIMIT`)                                              |
| requested_qty   | decimal(18,8) | NOT NULL     | 요청 수량                                                                  |
| limit_price     | decimal(18,8) | NULL         | 지정가                                                                    |
| time_in_force   | varchar(16)   | NULL         | 주문 유효 조건                                                               |
| reason          | varchar(255)  | NULL         | 주문 생성 사유                                                               |
| status          | varchar(32)   | NOT NULL     | 상태 (`CREATED`, `RISK_APPROVED`, `RISK_REJECTED`, `CONVERTED_TO_ORDER`) |
| idempotency_key | varchar(128)  | NULL         | 중복 방지 키                                                                |
| created_by      | varchar(64)   | NULL         | 생성 주체                                                                  |
| created_at      | timestamp     | NOT NULL     | 생성 시각                                                                  |
| updated_at      | timestamp     | NOT NULL     | 수정 시각                                                                  |

관계
- portfolio (1) : (N) order_intent
- instrument (1) : (N) order_intent

### 3-7. RiskRule

리스크 규칙 정의

역할
- 운영자나 Risk Manager가 관리하는 룰셋
- 종목별, 포트폴리오별, 전역 규칙 가능

| 컬럼명             | 타입           | 제약           | 설명                                                                  |
| --------------- | ------------ | ------------ | ------------------------------------------------------------------- |
| id              | bigint       | PK           | 규칙 ID                                                               |
| rule_code       | varchar(64)  | UK, NOT NULL | 규칙 코드                                                               |
| rule_type       | varchar(64)  | NOT NULL     | 규칙 유형                                                               |
| scope_type      | varchar(32)  | NOT NULL     | 적용 범위 (`GLOBAL`, `PORTFOLIO`, `INSTRUMENT`, `PORTFOLIO_INSTRUMENT`) |
| scope_value     | varchar(128) | NULL         | 범위 값                                                                |
| operator        | varchar(16)  | NOT NULL     | 비교 연산자                                                              |
| threshold_value | varchar(128) | NULL         | 기준 값                                                                |
| is_enabled      | boolean      | NOT NULL     | 활성화 여부                                                              |
| priority        | int          | NOT NULL     | 평가 우선순위                                                             |
| created_at      | timestamp    | NOT NULL     | 생성 시각                                                               |
| updated_at      | timestamp    | NOT NULL     | 수정 시각                                                               |



### 3-8. RiskCheckResult

특정 `OrderIntent` 또는 `Order`에 대한 검증 결과

역할:

- 어떤 규칙이 통과/실패했는지 저장
- 거절 사유 추적
- 감사 및 운영 조회

| 컬럼명             | 타입           | 제약           | 설명                                    |
| --------------- | ------------ | ------------ | ------------------------------------- |
| id              | bigint       | PK           | 결과 ID                                 |
| order_intent_id | bigint       | FK, NOT NULL | 주문 의도 ID                              |
| rule_id         | bigint       | FK, NOT NULL | 규칙 ID                                 |
| check_status    | varchar(16)  | NOT NULL     | 검사 결과 (`PASSED`, `FAILED`, `SKIPPED`) |
| failure_code    | varchar(64)  | NULL         | 실패 코드                                 |
| message         | varchar(255) | NULL         | 설명                                    |
| evaluated_value | varchar(128) | NULL         | 평가 값                                  |
| threshold_value | varchar(128) | NULL         | 기준 값                                  |
| checked_at      | timestamp    | NOT NULL     | 검사 시각                                 |

관계
- order_intent (1) : (N) risk_check_result
- risk_rule (1) : (N) risk_check_result

### 3-9. Order

OMS가 관리하는 실제 주문 aggregate root

역할
- 상태 머신의 대상
- 수정/취소/재전송/중복 방지의 기준
- 외부 시장과 연결되는 핵심 단위

| 컬럼명              | 타입            | 제약           | 설명       |
| ---------------- | ------------- | ------------ | -------- |
| id               | bigint        | PK           | 주문 ID    |
| order_number     | varchar(64)   | UK, NOT NULL | 주문 번호    |
| portfolio_id     | bigint        | FK, NOT NULL | 포트폴리오 ID |
| order_intent_id  | bigint        | FK, NOT NULL | 주문 의도 ID |
| instrument_id    | bigint        | FK, NOT NULL | 종목 ID    |
| side             | varchar(16)   | NOT NULL     | 매수/매도    |
| order_type       | varchar(16)   | NOT NULL     | 주문 유형    |
| time_in_force    | varchar(16)   | NULL         | 주문 유효 조건 |
| original_qty     | decimal(18,8) | NOT NULL     | 원주문 수량   |
| remaining_qty    | decimal(18,8) | NOT NULL     | 미체결 수량   |
| filled_qty       | decimal(18,8) | NOT NULL     | 누적 체결 수량 |
| limit_price      | decimal(18,8) | NULL         | 지정가      |
| avg_fill_price   | decimal(18,8) | NULL         | 평균 체결가   |
| status           | varchar(32)   | NOT NULL     | 주문 상태    |
| venue            | varchar(64)   | NULL         | 거래 venue |
| venue_order_id   | varchar(128)  | NULL         | 외부 주문 ID |
| rejection_code   | varchar(64)   | NULL         | 거절 코드    |
| rejection_reason | varchar(255)  | NULL         | 거절 사유    |
| idempotency_key  | varchar(128)  | NULL         | 중복 방지 키  |
| version          | bigint        | NOT NULL     | 낙관적 락 버전 |
| created_at       | timestamp     | NOT NULL     | 생성 시각    |
| updated_at       | timestamp     | NOT NULL     | 수정 시각    |



핵심 불변식:

- `filledQty <= originalQty`
- `remainingQty = originalQty - filledQty`
- `FILLED`면 `remainingQty = 0`
- unique (order_number)
- `CANCELED`면 추가 fill 불가가 아니라, cancel-fill race 고려 시 "최종 처리 로직"이 중요하다.

관계
- portfolio (1) : (N) orders
- order_intent (1) : (1) orders
- instrument (1) : (N) orders


### 3-10. OrderEvent

주문 상태 전이와 외부 이벤트의 전체 로그
- Requirement에서 "주문 이벤트 로그 조회 후 시간순 재생으로 현재 상태 재현" 시나리오를 명시한다.

역할
- 감사 추적
- 리플레이
- 장애 분석
- 현재 상태 재구성 가능성 확보

| 컬럼명          | 타입          | 제약           | 설명                                                     |
| ------------ | ----------- | ------------ | ------------------------------------------------------ |
| id           | bigint      | PK           | 이벤트 ID                                                 |
| order_id     | bigint      | FK, NOT NULL | 주문 ID                                                  |
| sequence_no  | bigint      | NOT NULL     | 주문 내 이벤트 순번                                            |
| event_type   | varchar(64) | NOT NULL     | 이벤트 유형                                                 |
| from_status  | varchar(32) | NULL         | 이전 상태                                                  |
| to_status    | varchar(32) | NULL         | 이후 상태                                                  |
| event_source | varchar(32) | NOT NULL     | 이벤트 소스 (`OMS`, `RISK_ENGINE`, `EXECUTION`, `OPERATOR`) |
| payload_json | text        | NULL         | 원본 payload                                             |
| occurred_at  | timestamp   | NOT NULL     | 실제 발생 시각                                               |
| created_at   | timestamp   | NOT NULL     | 저장 시각                                                  |

제약
- `(order_id, sequence_no)` unique

관계
- orders (1) : (N) order_event

### 3-11. Execution

체결 사실 자체를 표현하는 엔터티

역할
- 부분체결 여러 건 저장
- 후처리의 입력
- 평균 체결가 및 실현손익 계산 근거

| 컬럼명             | 타입            | 제약           | 설명                             |
| --------------- | ------------- | ------------ | ------------------------------ |
| id              | bigint        | PK           | 체결 ID                          |
| order_id        | bigint        | FK, NOT NULL | 주문 ID                          |
| execution_id    | varchar(128)  | UK, NOT NULL | 외부/시뮬레이터 체결 ID                 |
| execution_type  | varchar(32)   | NOT NULL     | 체결 유형 (`PARTIAL_FILL`, `FILL`) |
| fill_qty        | decimal(18,8) | NOT NULL     | 체결 수량                          |
| fill_price      | decimal(18,8) | NOT NULL     | 체결 가격                          |
| fill_notional   | decimal(18,8) | NOT NULL     | 체결 금액                          |
| fee_amount      | decimal(18,8) | NOT NULL     | 수수료                            |
| slippage_amount | decimal(18,8) | NOT NULL     | 슬리피지                           |
| executed_at     | timestamp     | NOT NULL     | 체결 시각                          |
| created_at      | timestamp     | NOT NULL     | 저장 시각                          |

관계
- orders (1) : (N) execution

### 3-12. PortfolioLedger

사후처리 결과를 남기는 원장성 엔터티

역할
- 포지션/현금/PnL 반영의 근거 기록
- 리플레이 없이도 사후처리 추적 가능
- 회계 원장처럼 "왜 현재 값이 이렇게 되었는가" 설명

| 컬럼명            | 타입            | 제약           | 설명                                                                                                   |
| -------------- | ------------- | ------------ | ---------------------------------------------------------------------------------------------------- |
| id             | bigint        | PK           | 원장 ID                                                                                                |
| portfolio_id   | bigint        | FK, NOT NULL | 포트폴리오 ID                                                                                             |
| order_id       | bigint        | FK, NULL     | 주문 ID                                                                                                |
| execution_id   | bigint        | FK, NULL     | 체결 ID                                                                                                |
| instrument_id  | bigint        | FK, NULL     | 종목 ID                                                                                                |
| entry_type     | varchar(32)   | NOT NULL     | 원장 유형 (`POSITION_INCREASE`, `POSITION_DECREASE`, `CASH_DEBIT`, `CASH_CREDIT`, `REALIZED_PNL`, `FEE`) |
| quantity_delta | decimal(18,8) | NULL         | 수량 변화                                                                                                |
| cash_delta     | decimal(18,8) | NULL         | 현금 변화                                                                                                |
| price          | decimal(18,8) | NULL         | 기준 가격                                                                                                |
| amount         | decimal(18,8) | NULL         | 금액                                                                                                   |
| occurred_at    | timestamp     | NOT NULL     | 발생 시각                                                                                                |
| created_at     | timestamp     | NOT NULL     | 저장 시각                                                                                                |

관계
- portfolio (1) : (N) portfolio_ledger
- orders (1) : (N) portfolio_ledger
- execution (1) : (N) portfolio_ledger
- instrument (1) : (N) portfolio_ledger

### 3-13. PnlSnapshot

평가손익/실현손익 조회 최적화용 스냅샷

역할
- 운영 대시보드 조회
- 특정 시점 손익 비교
- 장중 계산 캐시

| 컬럼명            | 타입            | 제약           | 설명             |
| -------------- | ------------- | ------------ | -------------- |
| id             | bigint        | PK           | 스냅샷 ID         |
| portfolio_id   | bigint        | FK, NOT NULL | 포트폴리오 ID       |
| instrument_id  | bigint        | FK, NULL     | 종목별 스냅샷이면 값 존재 |
| realized_pnl   | decimal(18,8) | NOT NULL     | 실현손익           |
| unrealized_pnl | decimal(18,8) | NOT NULL     | 평가손익           |
| market_value   | decimal(18,8) | NOT NULL     | 평가금액           |
| snapshot_at    | timestamp     | NOT NULL     | 스냅샷 기준 시각      |
| created_at     | timestamp     | NOT NULL     | 생성 시각          |

관계
- portfolio (1) : (N) pnl_snapshot
- instrument (1) : (N) pnl_snapshot

## 4. 엔터티 간 관계

핵심 관계:

- instrument 1 --- N market_price_snapshot
- instrument 1 --- N portfolio_position
- instrument 1 --- N order_intent
- instrument 1 --- N orders
- instrument 1 --- N portfolio_ledger
- instrument 1 --- N pnl_snapshot

- portfolio 1 --- N portfolio_position
- portfolio 1 --- N cash_balance
- portfolio 1 --- N order_intent
- portfolio 1 --- N orders
- portfolio 1 --- N portfolio_ledger
- portfolio 1 --- N pnl_snapshot

- order_intent 1 --- N risk_check_result
- order_intent 1 --- 1 orders

- risk_rule 1 --- N risk_check_result

- orders 1 --- N order_event
- orders 1 --- N execution
- orders 1 --- N portfolio_ledger

- execution 1 --- N portfolio_ledger


## 5. 구현 순서 추천

### 1단계
Instrument

Portfolio

PortfolioPosition

CashBalance

### 2단계
OrderIntent

RiskRule

RiskCheckResult

### 3단계
Order

OrderEvent

Execution

### 4단계
PortfolioLedger

PnlSnapshot

## 6. 상태값 초안

order_intent.status
- CREATED
- RISK_APPROVED
- RISK_REJECTED
- CONVERTED_TO_ORDER
- EXPIRED

orders.status
- CREATED
- RISK_APPROVED
- RISK_REJECTED
- SENT
- ACKED
- PARTIALLY_FILLED
- FILLED
- CANCEL_REQUESTED
- CANCELED
- REJECTED

risk_check_result.check_status
- PASSED
- FAILED
- SKIPPED

execution.execution_type
- PARTIAL_FILL
- FILL

portfolio_ledger.entry_type
- POSITION_INCREASE
- POSITION_DECREASE
- CASH_DEBIT
- CASH_CREDIT
- REALIZED_PNL
- FEE

