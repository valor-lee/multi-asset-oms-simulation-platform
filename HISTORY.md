# History

프로젝트 slice 작업 이력을 한곳에 모아 기록한다.

## cross-cutting

### 2026.05.17 slice

시간 의존 로직이 각 서비스 내부에서 직접 시스템 시간을 만들지 않도록 공통 `Clock` 주입 구조로 정리.

#### 이번 슬라이스에서 한 일

- `OmsCoreApplication`에 `Clock.systemUTC()` bean 추가
- `OrderIntentFactory`가 `Clock`을 생성자 주입으로 받도록 변경
- `PreTradeRiskCheckService`가 `Clock`을 생성자 주입으로 받도록 변경
- `OrderConversionService`가 `Clock`을 생성자 주입으로 받도록 변경
- 수동 주문 의도 생성 테스트에서 고정 clock을 사용하도록 변경
- order 변환 시 생성된 `Order`와 `CONVERTED_TO_ORDER` intent가 같은 변환 시각을 사용하도록 정리
- 중복 변환 요청에서 저장소의 최신 converted intent를 우선 사용해 `updatedAt`이 불필요하게 갱신되지 않도록 보강

#### 메모

- 생성/검사/변환 시각을 테스트에서 고정할 수 있어 시간 기반 검증이 안정적이다.
- 운영 환경에서는 Spring bean으로 등록된 UTC clock을 공통으로 사용한다.
- 서비스 내부에서 `Clock.systemUTC()`를 직접 호출하는 경로를 줄여 시간 정책을 한곳에서 관리하기 쉽게 했다.
- 하나의 order conversion 동작은 하나의 기준 시각을 사용하므로, order 생성 시각과 intent 변환 완료 시각을 안정적으로 비교할 수 있다.
- `Order.createdAt`과 `OrderIntent.updatedAt`이 같으면 "이 intent가 이 시점에 order로 전환됐다"는 관계가 로그, 테스트, 감사 추적에서 명확해진다.
- 이후 `OrderConvertedEvent` 같은 이벤트를 추가할 때도 `convertedAt` 하나를 order, intent, event의 기준 시각으로 공유할 수 있다.

#### 검증

- 실행 테스트: `./gradlew build`

## intent-generation

### 2026.04.25 slice

공통 `OrderIntent` 모델과 기본 생성 API를 만들고, 다른 모듈이 참조 가능한 안정된 계약부터 세우는 방향으로 진행.

#### 이번 슬라이스에서 한 일

- `OrderIntent`, `CreateOrderIntentCommand`를 추가해 주문 의도의 공통 입력/출력 모델을 정의
- `OrderIntentSourceType`, `OrderSide`, `OrderType`, `TimeInForce`, `OrderIntentStatus` enum 추가
- `OrderIntentFactory`를 추가해 주문 의도 생성 책임을 한곳으로 모음
- `manual`, `rebalancing`, `strategy` 경로별 request/service를 만들고 공통 factory를 사용하도록 연결
- `OrderIntentFactoryTest`를 추가해 핵심 생성 규칙을 테스트로 고정

#### 생성 규칙

- 필수값 검증은 Jakarta Validation 기반으로 수행
- `LIMIT` 주문은 `limitPrice` 필수
- `MARKET` 주문은 `limitPrice` 금지
- `idempotencyKey`가 비어 있으면 기본 UUID 생성
- 생성 시 초기 상태는 `CREATED`
- `createdAt`, `updatedAt`는 생성 시점으로 동일하게 세팅

#### 현재 코드 구조

- `model`
  주문 의도 공통 모델과 enum
- `application`
  `OrderIntentFactory`
- `manual`
  수동 주문 의도 요청/생성 서비스
- `rebalancing`
  리밸런싱 주문 의도 요청/생성 서비스
- `strategy`
  전략 신호 기반 주문 의도 요청/생성 서비스

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`
- 현재 테스트 범위
  - 정상적인 `LIMIT` 주문 생성
  - `idempotencyKey` 자동 생성
  - `LIMIT` 주문의 `limitPrice` 누락 거절
  - `MARKET` 주문의 `limitPrice` 포함 거절

#### 메모

- 현재 `intentId`는 도메인 식별자로 `UUID`를 사용
  - 아직 JPA 엔터티/DB 시퀀스 전략이 정해지지 않은 단계에서도 생성 시점에 바로 식별자를 부여할 수 있음
  - 메모리 단계, 테스트, 로그, 이후 이벤트 연결에서 즉시 참조 가능한 ID가 필요함
  - 여러 모듈이나 흐름에서 생성되더라도 충돌 가능성이 매우 낮음
  - 영속화 전략이 확정되기 전까지 도메인 모델을 독립적으로 다루기 쉬움
- 문서상의 DB 스키마와 영속화 방식은 이후 JPA 엔터티 설계 시 구체화 예정
- 다음 슬라이스에서는 `pre-trade-risk` 입력 계약 연결 또는 manual input API 연결을 고려

### 2026.04.28 slice

수동 주문 의도 생성 흐름을 HTTP API로 열어, 기존 factory 검증 규칙을 실제 입력 진입점에서 사용할 수 있게 함

#### 이번 슬라이스에서 한 일

- `POST /api/order-intents/manual` 엔드포인트 추가
- 수동 입력 요청은 기존 `ManualOrderIntentRequest`를 사용하고, 생성 결과는 `OrderIntent`로 반환
- `OrderIntentValidationException`을 `400 Bad Request` 응답으로 변환
- manual API controller 테스트 추가

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`

#### 다음 후보

- `pre-trade-risk` 입력 계약 설계
- 생성된 `OrderIntent`의 저장/조회 흐름 설계
- 상태 전이 모델 추가

## pre-trade-risk

### 2026.04.29 slice

`intent-generation`에서 생성된 `OrderIntent`를 사전 리스크 검사 입력으로 받을 수 있도록 기본 계약을 세움.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskCheckCommand` 추가
  - `OrderIntent`에서 리스크 검사에 필요한 기본 필드를 추출
- `PreTradeRiskDecision` 추가
  - `APPROVED`, `REJECTED`
- `PreTradeRiskCheckResult` 추가
  - 검사 대상 intent, decision, reason, checkedAt 반환
- `PreTradeRiskCheckService` 추가
  - 현재는 입력 계약을 고정하기 위한 최소 방어 규칙만 검사
- `PreTradeRiskCheckServiceTest` 추가

#### 현재 검사 규칙

- `requestedQty`가 없거나 0 이하이면 `REJECTED`
- `LIMIT` 주문인데 `limitPrice`가 없으면 `REJECTED`
- `limitPrice`가 0 이하이면 `REJECTED`
- 그 외는 `APPROVED`

#### 메모

- `OrderIntentFactory`에서 이미 기본 주문 생성 규칙을 검증한다는 전제를 둔다.
- 현재 단계에서는 계좌, 포지션, 주문 한도, 시장 가격 기반 notional 계산은 아직 수행하지 않는다.

#### 다음 후보

- portfolio/account exposure 입력 모델 추가
- 주문 금액 한도 검사
- risk 결과를 intent 상태 전이와 연결

### 2026.04.30 slice

전체 승인/거절 결과뿐 아니라 규칙별 검사 결과를 함께 반환하도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskRuleCode` 추가
  - `POSITIVE_QUANTITY`, `LIMIT_PRICE_REQUIRED`, `POSITIVE_LIMIT_PRICE`
- `PreTradeRiskRuleStatus` 추가
  - `PASSED`, `FAILED`, `SKIPPED`
- `PreTradeRiskRuleCheckResult` 추가
  - 규칙 코드, 상태, 메시지, 평가값, 기준값을 표현
- `PreTradeRiskCheckResult`에 `ruleResults` 추가
- `PreTradeRiskCheckService`가 개별 규칙 결과를 모아 전체 decision을 산출하도록 변경
- 규칙별 결과 상태 테스트 추가

#### 메모

- 현재 규칙은 코드 기반으로 고정되어 있지만, 구조는 문서의 `RiskRule` / `RiskCheckResult` 설계로 확장 가능하게 둔다.
- 하나 이상의 규칙이 `FAILED`이면 전체 decision은 `REJECTED`가 된다.

### 2026.05.01 slice

외부 한도값을 받아 주문 수량 한도 규칙을 평가할 수 있도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext` 추가
  - 현재는 `maxOrderQty`만 포함
- `MAX_ORDER_QUANTITY` 규칙 추가
- `PreTradeRiskCheckService.evaluateWithLimits(command, limitContext)` 추가
- `evaluateBasicRules(command)`는 한도값이 없는 기본 컨텍스트로 동작
- 최대 주문 수량 이하/초과/미설정 케이스 테스트 추가

#### 현재 검사 규칙 추가

- `maxOrderQty`가 없으면 `MAX_ORDER_QUANTITY`는 `SKIPPED`
- `requestedQty`가 `maxOrderQty` 이하이면 `PASSED`
- `requestedQty`가 `maxOrderQty`를 초과하면 `FAILED`, 전체 decision은 `REJECTED`

### 2026.05.04 slice

주문 수량 한도에 이어 주문 금액 한도 규칙을 평가할 수 있도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext`에 `maxOrderNotional` 추가
- `MAX_ORDER_NOTIONAL` 규칙 추가
- `requestedQty * limitPrice`로 주문 금액을 계산해 한도 이하/초과를 평가
- 기존 `PreTradeRiskLimitContext(maxOrderQty)` 생성 경로는 유지
- 주문 금액 한도 이하/초과/미설정 케이스 테스트 추가

#### 현재 검사 규칙 추가

- `maxOrderNotional`이 없으면 `MAX_ORDER_NOTIONAL`은 `SKIPPED`
- 주문 금액을 계산할 수 없으면 `MAX_ORDER_NOTIONAL`은 `SKIPPED`
- 주문 금액이 `maxOrderNotional` 이하이면 `PASSED`
- 주문 금액이 `maxOrderNotional`을 초과하면 `FAILED`, 전체 decision은 `REJECTED`

### 2026.05.04 slice

현재 보유 수량과 주문 수량을 합산해 예상 포지션 한도를 검사할 수 있도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext`에 `currentPositionQty`, `maxPositionQty` 추가
- `MAX_POSITION_QUANTITY` 규칙 추가
- BUY 주문은 `currentPositionQty + requestedQty`, SELL 주문은 `currentPositionQty - requestedQty`로 예상 포지션 계산
- 주문 반영 후 예상 포지션의 절대값을 `maxPositionQty`와 비교
- 예상 포지션 한도 이하/초과/미설정 케이스 테스트 추가

#### 현재 검사 규칙 추가

- `maxPositionQty`가 없으면 `MAX_POSITION_QUANTITY`는 `SKIPPED`
- 예상 포지션을 계산할 수 없으면 `MAX_POSITION_QUANTITY`는 `SKIPPED`
- 예상 포지션 절대값이 `maxPositionQty` 이하이면 `PASSED`
- 예상 포지션 절대값이 `maxPositionQty`를 초과하면 `FAILED`, 전체 decision은 `REJECTED`

### 2026.05.05 slice

한도값과 현재 포트폴리오 상태가 하나의 컨텍스트에 섞이지 않도록 risk check 입력 컨텍스트를 분리.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext`를 순수 한도값 컨텍스트로 정리
  - `maxOrderQty`, `maxOrderNotional`, `maxPositionQty`
- `PreTradeRiskExposureContext` 추가
  - 현재는 `currentPositionQty` 포함
- `PreTradeRiskCheckContext` 추가
  - limit context와 exposure context를 묶어 risk check에 전달
- 기존 limit context 기반 호출 경로는 `evaluateWithLimits(command, limitContext)`로 유지
  - exposure가 필요 없는 주문 수량/금액 한도 검사는 기존 호출 방식으로 계속 사용 가능
- 포지션 한도 검사는 `PreTradeRiskCheckContext`의 exposure context를 사용하도록 변경
- 검사 진입점 메서드를 목적에 맞게 `evaluateBasicRules`, `evaluateWithLimits`, `evaluateWithContext`로 정리

#### 메모

- 이후 현금, open order, market data 기반 규칙을 추가할 때 각각의 입력 컨텍스트를 독립적으로 확장할 수 있다.

### 2026.05.07 slice

남은 pre-trade risk 1차 규칙의 입력 계약과 평가 로직을 추가.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskOpenOrderContext` 추가
  - 현재는 `duplicateOpenOrderExists` 포함
- `PreTradeRiskMarketContext` 추가
  - 현재는 `lowerPriceBand`, `upperPriceBand` 포함
- `PreTradeRiskControlContext` 추가
  - 현재는 `killSwitchEnabled` 포함
- `PreTradeRiskCheckContext`에 open order context 추가
- `PreTradeRiskCheckContext`에 market context, control context 추가
- `DUPLICATE_OPEN_ORDER` 규칙 추가
- `PRICE_BAND` 규칙 추가
- `KILL_SWITCH` 규칙 추가
- 각 규칙의 `SKIPPED` / `PASSED` / `FAILED` 케이스 테스트 추가

#### 현재 검사 규칙 추가

- open order context가 없으면 `DUPLICATE_OPEN_ORDER`는 `SKIPPED`
- 중복 open order가 없으면 `DUPLICATE_OPEN_ORDER`는 `PASSED`
- 중복 open order가 있으면 `DUPLICATE_OPEN_ORDER`는 `FAILED`, 전체 decision은 `REJECTED`
- price band context가 없거나 주문 가격이 없으면 `PRICE_BAND`는 `SKIPPED`
- `limitPrice`가 price band 안이면 `PRICE_BAND`는 `PASSED`
- `limitPrice`가 price band 밖이면 `PRICE_BAND`는 `FAILED`, 전체 decision은 `REJECTED`
- kill switch context가 없으면 `KILL_SWITCH`는 `SKIPPED`
- kill switch가 꺼져 있으면 `KILL_SWITCH`는 `PASSED`
- kill switch가 켜져 있으면 `KILL_SWITCH`는 `FAILED`, 전체 decision은 `REJECTED`

#### 메모

- 실제 open order 조회, 시장 기준가/가격 밴드 조회, kill switch 설정 조회는 아직 외부 저장소/OMS 상태 모델에 연결하지 않고, risk check 입력 컨텍스트로 받은 조회 결과를 평가하는 계약만 세운다.

### 2026.05.09 slice

`PreTradeRiskCheckService`에 모여 있던 개별 규칙 평가 로직을 rule 컴포넌트로 분리.

#### 작업 배경

- 1차 risk rule이 늘어나면서 `PreTradeRiskCheckService`가 규칙 판단, 결과 생성, 실행 순서 관리, 전체 decision 집계를 모두 담당하게 됐다.
- 새 규칙을 추가할 때마다 service가 계속 커지고, 기존 규칙 수정과 전체 평가 흐름 수정이 같은 파일에서 섞이는 문제가 생겼다.
- 이후 rule enable/disable, rule 우선순위, rule별 테스트, 외부 조회 연동을 고려하면 규칙 단위의 독립성이 필요하다.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskRule` 인터페이스 추가
- `AbstractPreTradeRiskRule` 추가
  - 규칙 결과 생성 helper를 공통화
- 기존 규칙별 class 추가
  - `PositiveQuantityRule`
  - `LimitPriceRequiredRule`
  - `PositiveLimitPriceRule`
  - `MaxOrderQuantityRule`
  - `MaxOrderNotionalRule`
  - `MaxPositionQuantityRule`
  - `DuplicateOpenOrderRule`
  - `PriceBandRule`
  - `KillSwitchRule`
- `PreTradeRiskCheckService`는 rule list 실행과 전체 decision 집계만 담당하도록 축소
- `evaluateWithContext(command, null)` 호출 시 empty context로 평가하도록 방어

#### 구조 개선 효과

- `PreTradeRiskCheckService`의 책임이 rule 실행과 결과 집계로 좁아졌다.
- 개별 규칙의 판단 로직은 각 rule class에 모여 있어 수정 범위가 작아졌다.
- 새 규칙은 `PreTradeRiskRule` 구현체를 추가하고 rule list에 등록하는 방식으로 확장할 수 있다.
- 전체 `reason`은 첫 번째 failed rule의 메시지를 대표 사유로 유지하고, 상세 실패 정보는 기존처럼 `ruleResults`에서 모두 확인할 수 있다.
- rule list 순서가 대표 거절 사유의 우선순위 역할을 하므로 기존 동작과 호환된다.

#### interface / abstract 적용 이유

- interface는 구현체가 지켜야 할 기능 계약을 정의할 때 사용하고, abstract class는 여러 구현체가 공유하는 공통 로직이나 상태를 제공하면서 일부 동작은 하위 클래스에 맡기고 싶을 때 사용한다.
- `PreTradeRiskRule`은 모든 규칙이 따라야 하는 공통 계약이다.
  - 입력: `PreTradeRiskCheckCommand`, `PreTradeRiskCheckContext`
  - 출력: `PreTradeRiskRuleCheckResult`
  - service는 구체 rule 타입을 몰라도 동일한 방식으로 실행할 수 있다.
- `AbstractPreTradeRiskRule`은 각 rule에서 반복되는 결과 생성 코드를 공통화한다.
  - `passed(...)`, `failed(...)`, `skipped(...)`, `valueOf(...)`
  - 각 rule class는 비즈니스 판단 조건에 집중할 수 있다.
- interface는 실행 계약을 고정하고, abstract class는 구현 중복을 줄이는 역할로 분리했다.
- 현재 구조에서는 `PreTradeRiskRule`로 모든 rule의 실행 계약을 통일하고, `AbstractPreTradeRiskRule`로 rule 결과 생성 helper를 재사용하도록 분리했다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`

## post-trade

### 2026.05.21 slice

execution에서 완료된 order를 post-trade trade로 캡처하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `Trade`, `TradeStatus` 모델 추가
  - order, intent, portfolio, instrument, side, quantity, capture 시각 기록
- `TradeRepository` 포트와 `InMemoryTradeRepository` 추가
  - `tradeId` 기준 조회
  - `orderId` 기준 조회
- `TradeCaptureService` 추가
  - `FILLED` order는 전체 체결 수량을 trade로 캡처
  - `CANCELED` order는 체결 수량이 있으면 부분 체결 trade로 캡처
  - 이미 캡처된 order는 기존 trade를 반환해 중복 capture를 방어
  - 체결 수량이 없는 canceled order, open order, missing order는 예외 처리
- trade capture 서비스 테스트와 in-memory trade repository 테스트 추가

#### 메모

- execution의 `Order`는 주문 생명주기 상태를 관리하고, post-trade의 `Trade`는 실제 체결된 거래 결과를 관리한다.
- 부분 체결 후 취소된 order도 체결된 수량만큼은 post-trade 대상이므로 trade로 캡처한다.
- 아직 체결 가격/수수료/결제일은 모델링하지 않고, 다음 slice에서 average fill price 또는 settlement workflow로 확장할 수 있게 최소 trade 경계를 만든다.
- average fill price는 여러 체결 이벤트가 다른 가격으로 나뉘어 들어왔을 때 수량 가중 평균 체결가를 계산하는 값이다.
- settlement workflow는 캡처된 trade를 기준으로 수수료/세금, 현금/자산 이동, 결제일, 최종 정산 완료 상태를 관리하는 후처리 흐름이다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.22 slice

캡처된 trade를 결제 예정 상태로 전이하고, settlement 완료까지 관리하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `TradeStatus`에 `SETTLEMENT_PENDING`, `SETTLED` 추가
- `Trade`에 `settledAt` 추가
- `Settlement`, `SettlementStatus`, `SettlementException` 추가
- `SettlementRepository` 포트와 `InMemorySettlementRepository` 추가
  - `settlementId` 기준 조회
  - `tradeId` 기준 조회
- `SettlementService` 추가
  - `CAPTURED` trade를 settlement 예정으로 등록하고 `SETTLEMENT_PENDING`으로 전이
  - 이미 예정 등록된 trade는 기존 settlement를 반환해 중복 schedule을 방어
  - pending settlement를 완료 처리하고 trade를 `SETTLED`로 전이
  - 이미 완료된 settlement는 기존 settlement를 반환해 중복 confirm을 방어
- settlement schedule/confirm 서비스 테스트와 in-memory settlement repository 테스트 추가

#### 메모

- trade capture는 "거래가 발생했다"를 기록하는 단계이고, settlement는 "돈과 자산 이동이 결제일까지 마무리된다"를 관리하는 단계다.
- 현재는 settlement date와 상태 전이만 다루고, 실제 현금/자산 원장 반영은 이후 ledger slice에서 확장한다.
- settlement 생성과 trade 상태 전이는 실제 DB 적용 시 하나의 transaction으로 묶어야 한다.
  - settlement만 생성되고 trade가 `CAPTURED`로 남으면 동일 trade를 다시 settlement 예정으로 잡으려는 정합성 혼란이 생길 수 있다.
  - trade만 `SETTLEMENT_PENDING`으로 바뀌고 settlement row가 없으면 결제일, settlement id, 후속 정산 처리 대상을 찾을 수 없다.
  - settlement 완료와 trade `SETTLED` 전이도 함께 커밋되어야 정산 완료 여부를 조회하는 화면/배치/원장이 같은 상태를 보게 된다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.22 slice

settled trade를 position ledger에 반영하고, portfolio/instrument별 현재 포지션을 조회하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `PositionLedgerEntry`, `PositionKey`, `PositionLedgerException` 추가
- `PositionLedgerRepository` 포트와 `InMemoryPositionLedgerRepository` 추가
  - `entryId` 기준 조회
  - `tradeId` 기준 조회
  - portfolio/instrument 기준 현재 position 조회
- `PositionLedgerService` 추가
  - `SETTLED` trade만 position ledger에 posting
  - BUY trade는 양수 수량, SELL trade는 음수 수량으로 position delta 기록
  - 이미 posting된 trade는 기존 ledger entry를 반환해 중복 posting을 방어
  - portfolio/instrument별 current position 조회 제공
- position ledger posting 서비스 테스트와 in-memory position ledger repository 테스트 추가

#### 메모

- settlement는 결제 완료 상태를 관리하고, position ledger는 결제 완료된 거래가 실제 보유 수량에 미치는 변화를 기록한다.
- ledger entry는 trade별로 한 번만 생성되어야 하며, 같은 trade가 중복 posting되면 포지션이 실제보다 커지거나 작아질 수 있다.
- 현재는 수량 position만 관리하고, 현금/수수료/세금 원장은 이후 cash ledger slice에서 확장한다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.22 slice

execution fill event의 가격을 저장하고, post-trade trade capture 시 평균 체결가와 총 체결금액을 계산하도록 확장.

#### 이번 슬라이스에서 한 일

- `OrderFillExecution`에 `fillPrice` 추가
- `OrderFillExecutionRepository.findByOrderId(orderId)` 추가
- `InMemoryOrderFillExecutionRepository`가 order별 fill execution 조회를 지원하도록 확장
- `OrderFillService.fill(orderId, fillExecutionId, fillQuantity, fillPrice)` 추가
  - fill price가 있으면 양수인지 검증
  - 기존 fill API는 유지해 가격이 아직 없는 시뮬레이션 경로도 지원
- `Trade`에 `averageFillPrice`, `grossNotional` 추가
- `TradeCaptureService`가 order의 fill executions를 조회해 수량 가중 평균 체결가와 총 체결금액을 계산
- fill price 저장/조회 테스트와 trade capture 평균가 계산 테스트 추가

#### 메모

- average fill price는 여러 fill execution이 서로 다른 가격으로 들어왔을 때 `sum(fillQty * fillPrice) / sum(fillQty)`로 계산한다.
- 일부 fill execution에 가격이 없거나 가격이 있는 체결 수량 합계가 order의 체결 수량과 다르면 평균가와 총 체결금액은 비워둔다.
- 이후 cash ledger나 수수료/세금 계산은 `grossNotional`을 기준으로 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.23 slice

settled trade의 총 체결금액을 cash ledger에 반영하고, portfolio별 현재 현금 잔액을 조회하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `CashLedgerEntry`, `CashLedgerException` 추가
- `CashLedgerRepository` 포트와 `InMemoryCashLedgerRepository` 추가
  - `entryId` 기준 조회
  - `tradeId` 기준 조회
  - portfolio 기준 현재 cash 조회
- `CashLedgerService` 추가
  - `SETTLED` trade만 cash ledger에 posting
  - BUY trade는 음수 금액, SELL trade는 양수 금액으로 cash delta 기록
  - 이미 posting된 trade는 기존 ledger entry를 반환해 중복 posting을 방어
  - `grossNotional`이 없는 trade는 현금 반영 금액을 알 수 없으므로 거절
- cash ledger posting 서비스 테스트와 in-memory cash ledger repository 테스트 추가

#### 메모

- cash ledger는 position ledger와 함께 settlement 이후 실제 보유 수량과 현금 변화를 조회하기 위한 원장 역할을 한다.
- 이번 slice는 순수 거래대금만 반영하며, 수수료/세금/환전/통화별 잔액은 이후 별도 slice에서 확장한다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.23 slice

execution fill event의 수수료를 trade capture와 cash ledger까지 전달하도록 확장.

#### 이번 슬라이스에서 한 일

- `OrderFillExecution`에 `feeAmount` 추가
- `OrderFillService.fill(orderId, fillExecutionId, fillQuantity, fillPrice, feeAmount)` 추가
  - 수수료가 있으면 0 이상인지 검증
  - 기존 fill API는 유지해 수수료가 없는 체결 경로도 지원
- `Trade`에 `feeAmount` 추가
- `TradeCaptureService`가 가격이 있는 fill executions의 수수료를 합산해 trade에 저장
- `CashLedgerService`가 수수료가 있는 trade를 현금 변화에 반영
  - BUY는 `grossNotional + feeAmount`만큼 현금 유출
  - SELL은 `grossNotional - feeAmount`만큼 현금 유입
- fill 수수료 저장/검증, trade capture 수수료 합산, cash ledger 수수료 반영 테스트 추가

#### 메모

- 수수료가 없는 기존 체결 경로는 `feeAmount = null`로 유지한다.
- cash ledger는 수수료가 없으면 기존처럼 순수 거래대금만 반영하고, 수수료가 있으면 실제 현금 유출입에 포함한다.
- 세금, 슬리피지, 통화별 현금 잔액은 이후 별도 slice에서 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :execution:test :post-trade:test`

### 2026.05.25 slice

settlement 완료 이후 position ledger와 cash ledger를 함께 posting하는 post-settlement ledger workflow를 추가.

#### 이번 슬라이스에서 한 일

- `LedgerPostingResult`, `LedgerPostingException` 추가
- `PostSettlementLedgerService` 추가
  - `SETTLED` trade만 ledger posting 허용
  - `grossNotional`이 없는 trade는 cash posting 전에 거절
  - position ledger와 cash ledger에 같은 trade를 함께 posting
  - 이미 posting된 trade는 각 ledger service의 idempotency에 따라 기존 entry 반환
- post-settlement ledger posting 서비스 테스트 추가
  - BUY/SELL trade의 position/cash 동시 posting 검증
  - 중복 posting 방어 검증
  - posting 전 선검증으로 부분 posting이 생기지 않는 케이스 검증

#### 메모

- position ledger와 cash ledger는 각각 독립적으로 idempotent하지만, 실제 사용 흐름에서는 settlement 완료 후 두 원장을 함께 반영해야 한다.
- 이번 service는 이후 transaction boundary가 생겼을 때 하나의 application use case로 묶기 좋은 경계가 된다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.27 slice

execution fill event의 세금을 trade capture와 cash ledger까지 전달하도록 확장.

#### 이번 슬라이스에서 한 일

- `OrderFillExecution`에 `taxAmount` 추가
- `OrderFillService.fill(orderId, fillExecutionId, fillQuantity, fillPrice, feeAmount, taxAmount)` 추가
  - 세금이 있으면 0 이상인지 검증
  - 기존 fill API는 유지해 세금이 없는 체결 경로도 지원
- `Trade`에 `taxAmount` 추가
- `TradeCaptureService`가 가격이 있는 fill executions의 세금을 합산해 trade에 저장
- `CashLedgerService`가 세금이 있는 trade를 현금 변화에 반영
  - BUY는 `grossNotional + feeAmount + taxAmount`만큼 현금 유출
  - SELL은 `grossNotional - feeAmount - taxAmount`만큼 현금 유입
- fill 세금 저장/검증, trade capture 세금 합산, cash ledger 세금 반영 테스트 추가

#### 메모

- 세금이 없는 기존 체결 경로는 `taxAmount = null`로 유지한다.
- cash ledger는 수수료/세금이 없으면 기존처럼 순수 거래대금만 반영하고, 값이 있으면 실제 현금 유출입에 포함한다.
- 이후 국가/상품별 세금 정책을 별도 calculator로 분리할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :execution:test :post-trade:test`

### 2026.05.27 slice

settled SELL trade의 realized PnL을 기록하고 portfolio별 누적 realized PnL을 조회하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `RealizedPnlEntry`, `RealizedPnlException` 추가
- `RealizedPnlRepository` 포트와 `InMemoryRealizedPnlRepository` 추가
  - `entryId` 기준 조회
  - `tradeId` 기준 조회
  - portfolio 기준 누적 realized PnL 조회
- `RealizedPnlService` 추가
  - `SETTLED` SELL trade만 realized PnL posting 허용
  - 평균 원가 입력값을 받아 `grossNotional - quantity * averageCost - feeAmount - taxAmount`로 realized PnL 계산
  - 이미 posting된 trade는 기존 PnL entry를 반환해 중복 posting 방어
  - 평균 체결가, 총 체결금액, 평균 원가 누락/오류 케이스 방어
- realized PnL 저장소 테스트와 서비스 계산/검증 테스트 추가

#### 메모

- 현재는 평균 원가를 외부 입력으로 받는 계약을 먼저 세웠다.
- 이후 position cost basis나 lot 관리가 생기면 `averageCost`를 별도 cost basis service에서 조회하도록 연결할 수 있다.
- BUY trade는 realized PnL을 만들지 않으므로 이번 slice에서는 SELL trade만 대상으로 한다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.27 slice

현재 position, 평균 원가, 현재가를 기준으로 unrealized PnL snapshot을 계산하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `UnrealizedPnlSnapshot`, `UnrealizedPnlException` 추가
- `UnrealizedPnlService` 추가
  - `PositionLedgerService.currentPosition(portfolioId, instrumentId)`로 현재 position 조회
  - 평균 원가와 현재가를 입력받아 cost basis, market value, unrealized PnL 계산
  - 보유 수량이 없으면 0 quantity / 0 PnL snapshot 반환
  - short position은 아직 지원하지 않으므로 거절
  - portfolio/instrument/가격 입력 검증
- unrealized PnL 계산/검증 테스트 추가

#### 메모

- 평가손익은 시장 가격이 바뀔 때마다 달라지는 값이므로 ledger posting이 아니라 snapshot 계산으로 분리했다.
- 현재는 평균 원가와 현재가를 외부 입력으로 받는다.
- 이후 market data 조회와 position cost basis 조회가 생기면 service 내부 입력원을 연결할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.05.27 slice

order별 execution/fill 이벤트를 시간순 audit trail로 조회하는 기본 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `OrderExecutionEventRepository.findByOrderId(orderId)` 추가
- `InMemoryOrderExecutionEventRepository`가 order별 execution event 조회를 지원하도록 확장
- `audit-replay` 모듈이 `execution` 모듈 이벤트 모델을 조회할 수 있도록 의존성 추가
- `OrderAuditEventType`, `OrderAuditEvent`, `OrderAuditTrail` 추가
- `OrderAuditTrailService` 추가
  - ACK/REJECT/CANCEL confirmation 이벤트와 fill execution 이벤트를 order 기준으로 모음
  - 이벤트 발생 시각 기준으로 정렬해 하나의 timeline으로 반환
  - fill 이벤트의 수량, 가격, 수수료, 세금 정보를 audit trail에 포함
- order별 execution event 조회 테스트와 audit trail 서비스 테스트 추가

#### 메모

- 감사 로그는 "현재 order 상태가 왜 이렇게 되었는가"를 설명하기 위한 근거 데이터다.
- 주문은 ACK, 부분체결, 취소 요청, 취소 확인, 추가 체결처럼 여러 이벤트를 거쳐 최종 상태가 정해진다.
- 장애나 상태 불일치가 발생했을 때 최종 order row만 보면 원인을 알기 어렵고, 시간순 이벤트 trail이 있어야 어떤 broker/exchange 응답과 fill이 상태를 만들었는지 추적할 수 있다.
- 이번 slice는 아직 상태를 다시 계산하는 replay engine은 아니고, replay의 입력이 될 order event timeline을 안정적으로 조회하는 첫 단계다.
- fill 이벤트에는 수량/가격/수수료/세금이 들어가므로 이후 post-trade 결과와 주문 체결 이력을 대조하는 데에도 사용할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :execution:test :audit-replay:test`

### 2026.05.28 slice

audit trail 이벤트 타입 표현을 `source + eventType` 구조로 바꿔 원본 이벤트 enum과 audit enum의 중복 결합을 제거.

#### 이번 슬라이스에서 한 일

- `OrderAuditEventType` 제거
- `OrderAuditEventSource` 추가
  - `ORDER_EXECUTION`
  - `FILL_EXECUTION`
- `OrderAuditEvent`가 `source`와 원본 `eventType` 문자열을 함께 갖도록 변경
- `OrderAuditTrailService` 변환 로직 변경
  - execution event는 `source = ORDER_EXECUTION`, `eventType = event.eventType().name()`으로 보존
  - fill execution은 `source = FILL_EXECUTION`, `eventType = "FILL"`로 표현
- audit trail 테스트가 source와 원본 eventType을 검증하도록 수정

#### 메모

- 이전 구조는 audit 모듈의 `OrderAuditEventType`이 execution 모듈의 `OrderExecutionEventType` 값을 복제했다.
- 이 구조에서는 execution event type이 추가될 때 audit enum도 같이 수정해야 하고, 누락되면 `valueOf()` 변환에서 런타임 오류가 날 수 있다.
- audit trail의 핵심 책임은 원본 이벤트를 재정의하는 것이 아니라 "어느 소스에서 온 어떤 이벤트인가"를 안정적으로 보존하는 것이다.
- `source`는 audit에서 해석 가능한 안정된 분류이고, `eventType` 문자열은 원본 이벤트명을 그대로 담는 값이다.
- 앞으로 ACK/REJECT/CANCEL 외의 execution event가 추가되어도 audit enum을 매번 동기화하지 않아도 된다.
- 장기적으로는 fromStatus/toStatus와 payload를 포함한 공통 order lifecycle event log로 확장하면 replay 입력으로 더 강해진다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.29 slice

order audit trail을 순서대로 적용해 SENT 이후 실행 결과를 재현하는 기본 replay 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `OrderReplayResult`, `OrderReplayException` 추가
- `OrderExecutionReplayService` 추가
  - order audit trail을 조회해 발생 시각 순서대로 이벤트 적용
  - replay 시작 상태는 `SENT`로 고정
  - `ACKNOWLEDGED` 이벤트는 `ACKED` 상태로 재현
  - `REJECTED` 이벤트는 `REJECTED` 상태로 재현
  - `CANCEL_CONFIRMED` 이벤트는 `CANCELED` 상태로 재현
  - `FILL` 이벤트는 체결 수량을 누적해 `PARTIALLY_FILLED` 또는 `FILLED` 상태로 재현
  - 누적 체결 수량이 원 주문 수량을 초과하면 오류로 방어
- ACK, partial fill, full fill, reject, partial fill 후 cancel confirmation replay 테스트 추가

#### 메모

- replay는 저장된 이벤트만으로 "이 주문이 어떤 상태가 되었어야 하는가"를 다시 계산하는 기능이다.
- 운영 중 장애나 데이터 불일치가 발생했을 때 현재 order row와 이벤트 기반 재현 결과를 비교하면, 상태 전이 누락이나 중복 처리 여부를 추적할 수 있다.
- 이번 slice는 전체 주문 lifecycle replay가 아니라 `SENT` 이후 broker/exchange 응답과 fill 이벤트만 재현한다.
- 아직 `CREATED -> SENT`, `CANCEL_REQUESTED` 같은 내부 요청 이벤트는 audit trail에 저장하지 않으므로 replay 결과도 해당 구간은 재현하지 않는다.
- 이후 fromStatus/toStatus와 payload를 포함한 공통 order lifecycle event log가 생기면 replay를 더 엄밀한 상태 머신 검증으로 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

## execution

### 2026.05.17 slice

risk 승인된 `OrderIntent`를 실제 주문 처리 대상인 `Order`로 변환하는 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `Order`, `OrderStatus` 모델 추가
- `OrderRepository` 포트와 `InMemoryOrderRepository` 추가
  - `orderId` 기준 조회
  - `intentId` 기준 조회
- `OrderConversionService` 추가
  - `RISK_APPROVED` intent만 order로 변환
  - 변환된 order는 `CREATED` 상태로 저장
  - 변환이 끝난 intent는 `CONVERTED_TO_ORDER` 상태로 저장
- 이미 변환된 intent를 다시 요청하면 기존 order를 반환하도록 중복 변환을 방어
- order 저장소와 변환 서비스 테스트 추가
- `execution` 모듈이 `OrderIntent` 타입을 사용하도록 `intent-generation` 의존성 추가

#### 메모

- `OrderIntent`는 주문 의도이고, `Order`는 execution/OMS가 실제 주문 생명주기를 관리할 대상이다.
- 같은 `intentId`로 이미 생성된 order가 있으면 새 order를 만들지 않는다.
- 이번 slice는 execution simulator의 ACK/FILL 처리 전 단계로, risk 승인 intent를 실행 가능한 order로 넘기는 경계를 만든다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.18 slice

생성된 `Order`를 broker/exchange 전송 대상으로 표시하는 submission 상태 전이를 추가.

#### 이번 슬라이스에서 한 일

- `OrderStatus.SENT` 추가
- `OrderSubmissionService` 추가
  - `CREATED` order만 `SENT` 상태로 전이
  - `SENT` order에 대한 재요청은 기존 order를 반환
  - 존재하지 않는 order id는 예외 처리
- `OrderSubmissionException` 추가
- order submit 상태 전이와 중복 submit 요청 테스트 추가
- `OrderConversionService`의 public 변환 진입점을 `intentId` 기반으로 정리
  - 저장소에서 최신 intent를 조회한 뒤 변환
  - order는 있는데 intent가 없으면 정합성 오류로 보고 예외 처리

#### 메모

- 이번 slice는 `OrderConversionService`가 만든 `CREATED` order를 실제 실행 흐름의 다음 단계인 전송 상태로 넘기는 경계다.
- 아직 외부 broker/exchange API 호출은 붙이지 않고, 전송 요청이 접수되었음을 내부 상태로 고정한다.
- 이미 `SENT`인 order는 다시 저장하지 않아 최초 전송 시각인 `updatedAt`을 보존한다.
- `OrderIntent` 객체를 직접 넘기는 public API는 테스트 편의에 가까워 제거하고, 실무 흐름처럼 id 기반 조회로 최신 상태 정합성을 확인한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.19 slice

전송된 `Order`에 대한 broker/exchange ACK 또는 거절 응답 상태 전이를 추가.

#### 이번 슬라이스에서 한 일

- `OrderStatus.ACKED`, `OrderStatus.REJECTED` 추가
- `OrderAcknowledgementService` 추가
  - `SENT` order를 `ACKED` 상태로 전이
  - `SENT` order를 `REJECTED` 상태로 전이
  - `ACKED` / `REJECTED` order에 대한 재요청은 기존 order를 반환
  - 존재하지 않는 order id는 예외 처리
- `OrderAcknowledgementException` 추가
- order ack/reject 상태 전이와 중복 응답 요청 테스트 추가

#### 메모

- 이번 slice는 `SENT` 이후 broker/exchange가 주문을 접수했는지, 거절했는지를 내부 상태로 고정하는 단계다.
- 아직 실제 외부 broker 응답 수신은 붙이지 않고, 응답 결과를 서비스 호출로 반영한다.
- 이미 최종 응답 상태인 `ACKED` 또는 `REJECTED`는 다시 저장하지 않아 최초 응답 시각인 `updatedAt`을 보존한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.19 slice

ACK 이후 order 체결 수량을 누적 반영하는 fill 상태 전이를 추가.

#### 이번 슬라이스에서 한 일

- `Order`에 `filledQuantity` 추가
- `OrderStatus.PARTIALLY_FILLED`, `OrderStatus.FILLED` 추가
- `OrderFillService` 추가
  - `ACKED` order의 첫 체결을 반영
  - `PARTIALLY_FILLED` order의 추가 체결을 누적
  - 누적 체결 수량이 주문 수량보다 작으면 `PARTIALLY_FILLED`
  - 누적 체결 수량이 주문 수량과 같으면 `FILLED`
  - 주문 수량을 초과하는 체결은 예외 처리
- `OrderFillException` 추가
- partial fill, full fill, overfill, non-positive fill, 잘못된 상태 테스트 추가

#### 메모

- 이번 slice는 broker/exchange ACK 이후 실제 체결 이벤트를 내부 order 상태에 반영하는 단계다.
- 아직 체결 id 기반 중복 체결 방지는 없으므로, 같은 fill 이벤트의 idempotency는 이후 execution report 모델에서 다룬다.
- 기존 submission/acknowledgement 상태 전이에서는 `filledQuantity`를 그대로 보존한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.20 slice

ACK 또는 부분 체결 이후 order 취소 요청과 취소 완료 상태 전이를 추가.

#### 이번 슬라이스에서 한 일

- `OrderStatus.CANCEL_REQUESTED`, `OrderStatus.CANCELED` 추가
- `OrderCancellationService` 추가
  - `ACKED` order를 `CANCEL_REQUESTED` 상태로 전이
  - `PARTIALLY_FILLED` order를 `CANCEL_REQUESTED` 상태로 전이
  - `CANCEL_REQUESTED` order를 `CANCELED` 상태로 전이
  - 중복 cancel request / cancel confirmation은 기존 order를 반환
- `OrderCancellationException` 추가
- cancel request, cancel confirmation, 잘못된 상태, missing order 테스트 추가
- `CANCEL_REQUESTED` 상태에서도 fill을 허용해 cancel-fill race condition을 반영

#### 메모

- `CANCEL_REQUESTED` 이후에도 broker/exchange에서 fill이 먼저 도착할 수 있으므로, fill 서비스는 해당 상태의 체결 반영을 허용한다.
- `FILLED` order는 남은 수량이 없으므로 취소 요청 대상에서 제외한다.
- 아직 cancel reject 응답은 별도로 모델링하지 않고, 성공적인 cancel confirmation만 `CANCELED`로 반영한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.20 slice

broker/exchange 체결 이벤트가 재전송되더라도 같은 체결이 중복 누적되지 않도록 fill event idempotency를 추가.

#### 이번 슬라이스에서 한 일

- `OrderFillExecution` 모델 추가
  - `fillExecutionId`, `orderId`, `fillQuantity`, `processedAt` 기록
- `OrderFillExecutionRepository` 포트와 `InMemoryOrderFillExecutionRepository` 추가
- `OrderFillService.fill(orderId, fillExecutionId, fillQuantity)` 추가
  - 처음 보는 `fillExecutionId`는 order 체결 수량에 누적 반영
  - 이미 처리된 `fillExecutionId`는 중복 이벤트로 보고 수량을 다시 더하지 않음
  - 같은 `fillExecutionId`가 다른 order에 사용되면 정합성 오류로 예외 처리
- 기존 `fill(orderId, fillQuantity)` 진입점은 유지하되 내부적으로 새 체결 id를 생성하도록 연결
- fill execution 저장소 테스트와 중복 체결 이벤트 테스트 추가

#### 메모

- broker/exchange 메시지는 네트워크 재시도, consumer 재처리, 장애 복구 과정에서 같은 체결 이벤트가 다시 들어올 수 있다.
- 체결 수량은 누적값이므로 중복 이벤트를 그대로 더하면 포지션/잔고/주문 상태가 실제보다 커지는 문제가 생긴다.
- `fillExecutionId`를 별도로 저장하면 같은 이벤트를 다시 받았을 때 현재 order를 반환하면서 최초 반영 결과를 보존할 수 있다.
- 현재는 in-memory 저장소이므로 완전한 원자성은 없고, 실제 DB 적용 시 fill execution 저장과 order 상태 갱신을 하나의 transaction으로 묶어야 한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.21 slice

broker/exchange 응답 이벤트가 재전송되더라도 ACK/REJECT/CANCEL confirmation 상태 전이가 중복 저장되지 않도록 execution event idempotency를 추가.

#### 이번 슬라이스에서 한 일

- `OrderExecutionEvent`, `OrderExecutionEventType` 추가
  - `ACKNOWLEDGED`, `REJECTED`, `CANCEL_CONFIRMED` 이벤트 타입 기록
- `OrderExecutionEventRepository` 포트와 `InMemoryOrderExecutionEventRepository` 추가
- `OrderAcknowledgementService.acknowledge(orderId, eventId)` 추가
  - 처음 보는 ack event는 `SENT -> ACKED` 전이를 수행하고 이벤트를 저장
  - 이미 처리한 ack event는 중복 응답으로 보고 현재 order를 반환
- `OrderAcknowledgementService.reject(orderId, eventId)` 추가
  - 처음 보는 reject event는 `SENT -> REJECTED` 전이를 수행하고 이벤트를 저장
  - 이미 처리한 reject event는 중복 응답으로 보고 현재 order를 반환
- `OrderCancellationService.confirmCancel(orderId, eventId)` 추가
  - 처음 보는 cancel confirmation event는 `CANCEL_REQUESTED -> CANCELED` 전이를 수행하고 이벤트를 저장
  - 이미 처리한 cancel confirmation event는 중복 응답으로 보고 현재 order를 반환
- 같은 `eventId`가 다른 order 또는 다른 이벤트 타입으로 재사용되면 정합성 오류로 예외 처리
- 기존 `acknowledge(orderId)`, `reject(orderId)`, `confirmCancel(orderId)` 진입점은 유지하되 내부적으로 새 event id를 생성하도록 연결
- execution event 저장소 테스트와 ACK/REJECT/CANCEL confirmation 중복 이벤트 테스트 추가

#### 메모

- broker/exchange 응답은 네트워크 재시도, consumer 재처리, 장애 복구 과정에서 같은 메시지가 다시 들어올 수 있다.
- 상태 전이 이벤트에 idempotency가 없으면 동일 응답이 재처리될 때 `updatedAt`이 바뀌거나, 이후 상태가 진행된 order에서 과거 이벤트를 잘못 해석할 수 있다.
- 이벤트 id를 저장하면 같은 응답을 다시 받아도 현재 order를 반환하면서 최초 처리 결과와 시간을 보존할 수 있다.
- fill event는 수량 누적 이벤트라 `OrderFillExecution`으로 별도 관리하고, ACK/REJECT/CANCEL confirmation은 상태 전이 이벤트라 `OrderExecutionEvent`로 관리한다.
- 현재는 in-memory 저장소이므로 완전한 원자성은 없고, 실제 DB 적용 시 execution event 저장과 order 상태 갱신을 하나의 transaction으로 묶어야 한다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

### 2026.05.17 slice

pre-trade risk 평가로 상태 전이된 `OrderIntent`를 저장소에 반영.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskOrderIntentService`가 `OrderIntentRepository`를 사용하도록 변경
- risk 평가 결과로 만든 새 `OrderIntent` 스냅샷을 repository에 저장한 뒤 반환
- risk 승인/거절 테스트에서 전이된 intent가 저장소에 반영되는지 검증

#### 메모

- 이전 slice에서는 risk 평가 결과가 새 `OrderIntent`로 반환되기만 했다.
- 이번 slice부터는 `RISK_APPROVED` 또는 `RISK_REJECTED` 상태가 저장소에도 남아 이후 실행/조회 흐름에서 이어 사용할 수 있다.
- 기존 원본 intent를 직접 변경하지 않고, 전이된 새 스냅샷을 저장하는 방식은 유지한다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`

### 2026.05.15 slice

생성된 `OrderIntent`를 저장하고 다시 조회할 수 있는 최소 저장소 인터페이스를 추가.

#### 이번 슬라이스에서 한 일

- `OrderIntentRepository` 포트 추가
  - `save(intent)`
  - `findByIntentId(intentId)`
  - `findByIdempotencyKey(idempotencyKey)`
- `InMemoryOrderIntentRepository` 추가
  - 현재 단계에서는 DB/JPA 없이 메모리 기반 저장소 인터페이스를 먼저 고정
- `ManualOrderIntentService`, `RebalancingOrderIntentService`, `StrategyOrderIntentService`가 생성한 intent를 repository에 저장하도록 변경
- manual intent 생성 서비스 저장 동작 테스트 추가
- 인메모리 repository 조회 동작 테스트 추가

#### 메모

- 아직 영속화 구현은 붙이지 않고, 이후 JPA/DB 전환을 위해 application 포트와 in-memory adapter를 분리했다.
- `idempotencyKey` 조회는 null 입력 시 `Optional.empty()`를 반환하도록 방어한다.

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`

### 2026.05.09 slice

pre-trade risk 평가 결과를 `OrderIntent` 상태 전이와 연결.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskOrderIntentService` 추가
  - `OrderIntent`를 risk check command로 변환해 평가
  - risk decision에 따라 새 `OrderIntent` 인스턴스의 status를 전이
- `PreTradeRiskOrderIntentResult` 추가
  - 상태 전이된 `OrderIntent`와 상세 `PreTradeRiskCheckResult`를 함께 반환
- `PreTradeRiskTransitionException` 추가
  - `CREATED` 상태가 아닌 intent는 pre-trade risk 평가 대상에서 제외
- risk 승인 시 `CREATED -> RISK_APPROVED` 전이
- risk 거절 시 `CREATED -> RISK_REJECTED` 전이
- 상태 전이 시 `createdAt`은 유지하고 `updatedAt`은 risk check 시각으로 갱신

#### 메모

- 기존 `OrderIntent`는 record라 직접 변경하지 않고 새 인스턴스를 반환한다.
- `OrderIntent`는 주문 흐름의 특정 시점 상태를 표현하는 불변 스냅샷으로 다룬다.
  - 기존 intent는 `CREATED` 상태 그대로 유지한다.
  - risk 평가 결과는 `RISK_APPROVED` 또는 `RISK_REJECTED` 상태의 새 intent로 표현한다.
- 불변 객체로 상태 전이를 표현하면 상태 전이 전/후가 명확하게 구분된다.
  - 감사 로그나 테스트에서 "risk 평가 전 CREATED intent"와 "risk 평가 후 intent"를 안정적으로 비교할 수 있다.
  - 기존 객체를 직접 수정할 때 생길 수 있는 예상치 못한 상태 변경 부작용을 줄인다.
- 여러 흐름이 같은 intent를 참조하는 상황에서도 기존 객체가 변하지 않는다.
  - 예를 들어 risk 평가 흐름이 새 `RISK_APPROVED` intent를 만들더라도, audit logging 흐름이 들고 있던 기존 intent는 계속 `CREATED` 상태로 남는다.
  - 같은 객체 참조를 공유할 때 타이밍에 따라 status가 달라지는 동시성 문제를 줄일 수 있다.
- `PreTradeRiskCheckService`는 계속 순수 risk 평가를 담당하고, intent 상태 전이는 별도 service에 둔다.
- 결과의 `riskCheckResult.ruleResults`에는 기존처럼 상세 규칙별 판단 결과가 보존된다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`
