# History

프로젝트 slice 작업 이력을 한곳에 모아 기록한다.

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
