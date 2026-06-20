# History

프로젝트 slice 작업 이력을 한곳에 모아 기록한다.

## pre-trade-risk

### 2026.06.21 slice

pre-trade risk controller를 얇게 유지하도록 intent 조회 책임을 application service로 이동.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskOrderIntentController`에서 `OrderIntentQueryService` 의존성 제거
  - controller는 path variable과 request body를 받아 service use case만 호출
  - intent 조회, 상태 검증, context 병합, 상태 전이는 service가 담당
- `PreTradeRiskOrderIntentService`에 `intentId` 기반 평가 메서드 추가
  - `evaluate(UUID, PreTradeRiskCheckContext)`
  - `evaluateWithLatestPriceBand(UUID, PreTradeRiskCheckContext, BigDecimal)`
  - `evaluateWithLatestPriceBandAndDuplicateOpenOrder(UUID, PreTradeRiskCheckContext, BigDecimal)`
- controller/service 테스트를 새 책임 분리에 맞게 갱신
- `docs/restful-api-strategy.md`의 controller 작성 기준 보강

#### 메모

- controller가 query service로 domain을 먼저 조회한 뒤 use case service에 넘기면 HTTP 계층이 application flow를 너무 많이 알게 된다.
- service가 `intentId`를 받아 내부에서 조회하면 HTTP, batch, scheduler 같은 다른 진입점도 같은 use case를 재사용하기 쉽다.
- 전체 컨트롤러를 훑었을 때 이번 패턴이 두드러진 곳은 pre-trade risk controller였고, 다른 모듈의 controller는 대부분 이미 service 위임 형태를 유지하고 있다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`
- 전체 빌드: `./gradlew build`

### 2026.06.20 slice

market-data latest price와 execution duplicate open order 조회를 함께 사용해 risk context를 자동 구성하는 API를 추가.

#### 이번 슬라이스에서 한 일

- `POST /api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band/duplicate-open-order` 추가
  - `priceBandRate`로 latest price 기준 price band context 구성
  - 평가 대상 `OrderIntent`의 portfolio/instrument/side/orderType/quantity/limitPrice/timeInForce로 duplicate open order 조회
  - 현재 평가 중인 intent id는 duplicate 후보에서 제외
- `PreTradeRiskLatestPriceBandDuplicateEvaluationRequest` 추가
  - limit, exposure, control context와 `priceBandRate`만 요청으로 받음
  - duplicate open order 여부는 서버 내부 조회 결과로 채움
- `PreTradeRiskOrderIntentService.evaluateWithLatestPriceBandAndDuplicateOpenOrder()` 추가
- `pre-trade-risk` 모듈에서 `execution` duplicate query service를 사용할 수 있도록 의존성 추가
- service/controller 테스트 추가
- `docs/pre-trade-risk-api.md`, `docs/restful-api-strategy.md` 갱신

#### 메모

- 기본 risk evaluation API는 호출자가 모든 context를 직접 넘기는 시뮬레이션/오케스트레이션 경계로 유지한다.
- latest price band API는 price context만 서버가 만들고, duplicate context는 호출자가 넘기는 중간 단계다.
- 이번 API는 price context와 duplicate open order context를 모두 서버 내부에서 조회해 구성하므로 MVP 주문 흐름에 더 가깝다.
- 같은 payload지만 다른 `idempotencyKey`로 생성된 주문 의심 케이스는 idempotency가 아니라 duplicate open order rule에서 거절한다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`
- 전체 빌드: `./gradlew build`

## execution

### 2026.06.20 slice

pre-trade risk의 duplicate open order context를 채우기 위한 execution 조회 API를 추가.

#### 이번 슬라이스에서 한 일

- `GET /api/orders/duplicate-open-order` 추가
  - `portfolioId`, `instrumentId`, `side`, `orderType`, `quantity`, `limitPrice`, `timeInForce` 기준으로 같은 open order 조회
  - `excludeIntentId`를 받으면 해당 intent에서 생성된 order는 후보에서 제외
- `DuplicateOpenOrderQueryService` 추가
  - open 상태: `CREATED`, `SENT`, `ACKED`, `PARTIALLY_FILLED`, `CANCEL_REQUESTED`
  - closed 상태: `FILLED`, `CANCELED`, `REJECTED`
- `DuplicateOpenOrderResult` 추가
  - `duplicateOpenOrderExists`, `duplicateOpenOrderId`, `duplicateOpenOrder` 응답
- service/controller 테스트 추가
- `docs/execution-api.md`, `docs/pre-trade-risk-api.md`, `docs/restful-api-strategy.md` 갱신

#### 메모

- pre-trade risk의 `DuplicateOpenOrderRule`은 context를 평가하는 규칙이고, 이번 API는 그 context를 만들기 위한 execution 조회 경계다.
- 같은 payload지만 다른 `idempotencyKey`로 생성된 주문 의심 케이스는 이 조회 결과를 risk evaluation request의 `duplicateOpenOrderExists`, `duplicateOpenOrderId`로 넘기면 된다.
- `CANCEL_REQUESTED`는 아직 취소 확정 전이므로 중복 주문 방지 관점에서는 open order로 본다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`

## pre-trade-risk

### 2026.06.19 slice

market-data의 latest market price를 사용해 price band context를 구성하는 pre-trade risk 평가 API를 추가.

#### 이번 슬라이스에서 한 일

- `POST /api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band` 추가
  - `priceBandRate`를 받아 latest price 기준 허용 가격 구간 계산
  - `lowerPriceBand = latestPrice * (1 - priceBandRate)`
  - `upperPriceBand = latestPrice * (1 + priceBandRate)`
- `PreTradeRiskLatestPriceBandEvaluationRequest` 추가
  - 기존 risk context 필드 중 market price band를 제외하고 `priceBandRate`를 받음
  - `priceBandRate` 누락/범위 오류는 `400 Bad Request`
- `PreTradeRiskOrderIntentService.evaluateWithLatestPriceBand()` 추가
- `PreTradeRiskExceptionHandler`에 market price missing과 request validation 예외 매핑 추가
- service/controller 테스트 추가
- `docs/pre-trade-risk-api.md`, `docs/market-data-api.md`, `docs/restful-api-strategy.md` 갱신

#### 메모

- 기존 risk evaluation API는 호출자가 `lowerPriceBand`, `upperPriceBand`를 직접 전달하는 시뮬레이션/오케스트레이션 경계로 유지한다.
- 새 latest price band API는 market-data가 관리하는 최신 가격으로 price band를 구성하므로 운영 흐름에 더 가깝다.
- 실제 거래소/증권사 시세 연동은 아직 없고, market-data latest price가 먼저 저장되어 있어야 한다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`

## post-trade

### 2026.06.12 slice

post-trade unrealized PnL 계산이 market-data의 latest market price를 사용할 수 있도록 API를 확장.

#### 이번 슬라이스에서 한 일

- `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest` 추가
  - `averageCost`는 요청값으로 받고, `marketPrice`는 market-data latest price에서 조회
  - 저장된 latest market price가 없으면 `404 Not Found`
- `UnrealizedPnlService`에 `snapshotWithLatestMarketPrice()` 추가
- `PostTradeExceptionHandler`에 `MarketPriceNotFoundException` 매핑 추가
- PnL controller/service 테스트 추가
- `docs/post-trade-api.md`, `docs/market-data-api.md`, `docs/restful-api-strategy.md` 갱신

#### 메모

- 기존 `unrealized-pnl?averageCost=...&marketPrice=...` API는 테스트/시뮬레이션처럼 호출자가 가격을 직접 넘기는 경계로 유지한다.
- 새 latest endpoint는 운영 흐름에 더 가깝게 market-data가 관리하는 최신 가격을 사용한다.
- 평균 원가는 아직 position lot이나 average cost ledger가 없으므로 계속 요청값으로 받는다.
- 이후 average cost 저장 구조가 생기면 `averageCost`도 서버 내부에서 조회하도록 바꿀 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

## market-data

### 2026.06.11 slice

pre-trade risk와 post-trade PnL에서 참조할 수 있는 instrument별 최신 시장 가격 API를 추가.

#### 이번 슬라이스에서 한 일

- `PUT /api/market-data/instruments/{instrumentId}/prices/latest` 추가
  - instrument별 latest market price 저장/교체
  - `price`는 필수이며 0보다 커야 함
  - `observedAt`이 없으면 서버 저장 시각을 관측 시각으로 사용
- `GET /api/market-data/instruments/{instrumentId}/prices/latest` 추가
  - instrument별 latest market price 조회
- `MarketPrice`, `MarketPriceService`, `MarketPriceRepository` 추가
- `InMemoryMarketPriceRepository` 추가
- `MarketDataExceptionHandler`로 400/404 오류 응답 정리
- service, repository, controller 테스트 추가
- `docs/market-data-api.md`, `docs/restful-api-strategy.md`, `README.md` 갱신

#### 메모

- 이번 API는 가격 시계열 전체가 아니라 "현재 최신 가격"만 다루는 MVP 경계다.
- latest price는 instrument별 하나의 리소스를 교체하는 성격이므로 `PUT`으로 열었다.
- 이후 price band context, unrealized PnL의 `marketPrice` 입력을 이 market-data service에서 가져오도록 연결할 수 있다.
- market replay나 과거 가격 조회가 필요해지면 latest price와 별도로 price tick/history 저장소를 추가한다.

#### 검증

- 실행 테스트: `./gradlew :market-data:test`

## post-trade

### 2026.06.07 slice

정산/원장 반영 이후 realized PnL과 unrealized PnL을 HTTP API로 확인할 수 있도록 post-trade API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/post-trade/trades/{tradeId}/realized-pnl-postings` 추가
  - `Trade(SETTLED, SELL)`의 realized PnL을 entry로 posting
  - `averageCost` 요청값을 받아 `grossNotional - quantity * averageCost - feeAmount - taxAmount`로 계산
  - 이미 posting된 trade는 기존 realized PnL entry 반환
- `GET /api/post-trade/portfolios/{portfolioId}/realized-pnl` 추가
  - portfolio 기준 현재 누적 realized PnL 응답
- `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl` 추가
  - 현재 position, 평균 원가, 시장가 기준 unrealized PnL snapshot 응답
- `RealizedPnlPostRequest`, `CurrentRealizedPnlResponse`, `PnlController` 추가
- `RealizedPnlService`의 trade 미존재 예외를 `TradeNotFoundException`으로 정리
- `PostTradeExceptionHandler`에 realized/unrealized PnL 예외 매핑 추가
- PnL controller 테스트 추가
- `docs/post-trade-api.md`, `docs/restful-api-strategy.md`에 PnL API 사용법과 endpoint 추가

#### 메모

- realized PnL은 실제 매도가 확정된 뒤 생기는 손익이므로 `SELL` trade를 대상으로 entry를 posting한다.
- unrealized PnL은 아직 보유 중인 position을 현재 시장가로 평가한 손익이다.
- 시장가는 계속 바뀌므로 unrealized PnL은 저장하지 않고 조회 시점의 snapshot으로 계산한다.
- realized PnL posting은 상태를 만드는 명령이므로 `POST`, realized/unrealized 조회는 상태를 변경하지 않으므로 `GET`으로 둔다.
- 현재는 평균 원가를 API 요청에서 받지만, 이후 position lot 또는 average cost ledger가 생기면 서버가 원가를 직접 계산하도록 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`

### 2026.06.07 slice

post-settlement ledger posting 이후 portfolio별 현재 position/cash를 조회할 수 있도록 post-trade 조회 API를 추가.

#### 이번 슬라이스에서 한 일

- `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}` 추가
  - portfolio/instrument 기준 현재 position quantity 응답
- `GET /api/post-trade/portfolios/{portfolioId}/cash` 추가
  - portfolio 기준 현재 cash balance 응답
- `CurrentPositionResponse`, `CurrentCashResponse` 추가
- ledger balance controller 테스트 추가
- `docs/post-trade-api.md`, `docs/restful-api-strategy.md`에 balance query API 사용법과 endpoint 추가

#### 메모

- 이번 API는 원장 posting 이후 운영자/클라이언트가 실제 보유 수량과 현금 잔액을 확인하는 조회 경계다.
- position/cash 조회는 상태를 변경하지 않으므로 `GET`으로 둔다.
- ledger entry가 아직 없으면 repository 누적값이 `0`으로 응답된다.
- BUY trade는 position을 증가시키고 cash를 감소시키며, SELL trade는 position을 감소시키고 cash를 증가시킨다.
- 이후 DB 전환 시 portfolio/instrument 기준 단건 조회 인덱스를 붙이면 현재 API 계약을 유지할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`
- 전체 빌드: `./gradlew build`

### 2026.06.07 slice

정산 완료된 trade를 position ledger와 cash ledger에 함께 posting할 수 있도록 post-trade API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/post-trade/trades/{tradeId}/ledger-postings` 추가
  - `Trade(SETTLED)`를 position ledger와 cash ledger에 함께 posting
  - 이미 posting된 trade는 각 ledger service의 idempotency에 따라 기존 entry 반환
  - position/cash ledger entry를 함께 응답
- `PostSettlementLedgerService`의 trade 미존재 예외를 `TradeNotFoundException`으로 정리
- `PostTradeExceptionHandler`에 ledger posting 예외 매핑 추가
  - 존재하지 않는 trade는 `404 Not Found`
  - posting 가능한 상태가 아니거나 `grossNotional`이 없으면 `409 Conflict`
- post-settlement ledger controller 테스트 추가
- `docs/post-trade-api.md`, `docs/restful-api-strategy.md`에 ledger posting API 사용법과 endpoint 추가

#### 메모

- 이번 API는 `Trade(SETTLED)` 이후 실제 보유 수량과 현금 변화를 조회 가능한 원장으로 확정하는 단계다.
- position ledger는 자산 수량 변화를 기록하고, cash ledger는 현금 증감을 기록한다.
- BUY trade는 position을 증가시키고 cash를 감소시킨다. SELL trade는 position을 감소시키고 cash를 증가시킨다.
- cash posting은 총 체결금액이 필요하므로 `grossNotional`이 없는 trade는 거절한다.
- 실제 DB 적용 시 position/cash 두 ledger posting은 하나의 transaction으로 묶는 것이 안전하다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`
- 전체 빌드: `./gradlew build`

### 2026.06.07 slice

캡처된 trade를 settlement 예정 상태로 등록하고 settlement 완료를 HTTP API로 확인할 수 있도록 post-trade API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/post-trade/trades/{tradeId}/settlements` 추가
  - `Trade(CAPTURED)`를 settlement 예정으로 등록
  - settlement 생성 후 trade는 `SETTLEMENT_PENDING` 상태로 전이
  - 이미 settlement가 있는 trade는 기존 settlement 반환
- `POST /api/post-trade/settlements/{settlementId}/confirmations` 추가
  - `Settlement(PENDING)`을 `SETTLED` 상태로 전이
  - 연결된 trade도 `SETTLED` 상태로 전이
  - 이미 `SETTLED`인 settlement는 중복 완료 요청으로 보고 기존 settlement 반환
- `SettlementScheduleRequest` 추가
  - `settlementDate` 누락은 `400 Bad Request`
- `TradeNotFoundException`, `SettlementNotFoundException`, `PostTradeRequestException` 추가
  - post-trade API에서 400/404/409를 명확히 구분
- settlement controller 테스트 추가
- `docs/post-trade-api.md`, `docs/restful-api-strategy.md`에 settlement API 사용법과 endpoint 추가

#### 메모

- 이번 API는 `Trade(CAPTURED) -> Settlement(PENDING) -> Settlement(SETTLED)` 흐름을 HTTP 레벨로 연결하는 단계다.
- trade capture는 "거래가 발생했다"를 기록하고, settlement는 "결제일에 돈과 자산 이동을 완료한다"를 관리한다.
- settlement schedule과 trade `SETTLEMENT_PENDING` 전이는 실제 DB 적용 시 하나의 transaction으로 묶어야 한다.
- settlement confirmation과 trade `SETTLED` 전이도 함께 커밋되어야 정산 상태 조회와 후속 원장 반영이 같은 사실을 보게 된다.
- 다음 slice에서는 `Trade(SETTLED)`를 position/cash ledger에 함께 posting하는 API를 열 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`
- 전체 빌드: `./gradlew build`

### 2026.06.07 slice

execution에서 체결이 끝난 order를 HTTP API로 post-trade trade로 capture할 수 있도록 진입점을 추가.

#### 이번 슬라이스에서 한 일

- `POST /api/post-trade/orders/{orderId}/trades` 추가
  - `FILLED` order를 `Trade(CAPTURED)`로 capture
  - 체결 수량이 있는 `CANCELED` order도 부분 체결 trade로 capture
  - 이미 capture된 order는 기존 trade 반환
- `PostTradeExceptionHandler` 추가
  - 존재하지 않는 order는 `404 Not Found`
  - capture 가능한 상태가 아닌 order는 `409 Conflict`
- `TradeCaptureService`의 order 미존재 예외를 `OrderNotFoundException`으로 정리
- trade capture controller 테스트 추가
- `docs/post-trade-api.md` 추가
- `README.md`, `docs/restful-api-strategy.md`에 post-trade API 링크와 endpoint 추가

#### 메모

- 이번 API는 MVP 주문 흐름에서 execution과 post-trade를 HTTP 레벨로 연결하는 단계다.
- execution의 `Order`는 주문 상태 머신을 관리하고, post-trade의 `Trade`는 실제 체결된 거래 결과를 정산/원장/PnL로 넘기기 위한 객체다.
- 부분 체결 후 취소된 order도 체결된 수량은 실제 거래 결과이므로 trade capture 대상이다.
- trade capture는 fill execution들을 요약해 평균 체결가, 총 체결금액, 수수료, 세금을 trade에 반영한다.
- 다음 slice에서는 `Trade(CAPTURED)`를 settlement 예정 상태로 넘기는 API를 열 수 있다.

#### 검증

- 실행 테스트: `./gradlew :post-trade:test`
- 전체 빌드: `./gradlew build`

## execution

### 2026.06.06 slice

주문 취소 요청과 broker/exchange 취소 완료 이벤트를 HTTP API로 반영할 수 있도록 execution API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/orders/{orderId}/cancel-requests` 추가
  - `ACKED` order를 `CANCEL_REQUESTED` 상태로 전이
  - `PARTIALLY_FILLED` order의 남은 수량 취소 요청을 `CANCEL_REQUESTED` 상태로 표현
  - 이미 `CANCEL_REQUESTED`인 order는 중복 요청으로 보고 기존 order 반환
- `POST /api/orders/{orderId}/cancel-confirmations` 추가
  - broker/exchange 취소 완료 이벤트를 받아 `CANCEL_REQUESTED` order를 `CANCELED` 상태로 전이
  - 같은 cancel confirmation `eventId` 재요청은 중복 이벤트로 보고 현재 order 반환
- `OrderCancellationService`의 order 미존재 예외를 `OrderNotFoundException`으로 정리
- `ExecutionExceptionHandler`에 cancel 예외 매핑 추가
- cancellation controller 테스트 추가
- `docs/execution-api.md`에 cancel request/confirmation API 사용법 추가
- `docs/restful-api-strategy.md`의 현재 API 목록 갱신

#### 메모

- 이번 API는 MVP 주문 상태 머신에서 취소 경로를 HTTP 레벨로 연결하는 단계다.
- 취소 요청과 취소 완료는 같은 동작이 아니다. `CANCEL_REQUESTED`는 취소 요청을 보낸 상태이고, `CANCELED`는 broker/exchange가 취소 완료를 확인한 상태다.
- 부분 체결 주문은 남은 수량만 취소될 수 있으므로 `PARTIALLY_FILLED -> CANCEL_REQUESTED`를 허용한다.
- cancel confirmation도 외부 이벤트이므로 `eventId`를 받아 중복 반영을 방어한다.
- `CANCEL_REQUESTED` 상태에서도 fill API가 체결을 허용하므로, 취소 요청과 체결 이벤트가 교차 도착하는 cancel-fill race condition을 표현할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 전체 빌드: `./gradlew build`

### 2026.06.05 slice

broker/exchange 체결 이벤트를 HTTP API로 반영해 order 체결 수량과 상태를 갱신할 수 있도록 execution API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/orders/{orderId}/fills` 추가
  - `ACKED` order의 첫 체결을 반영해 `PARTIALLY_FILLED` 또는 `FILLED` 상태로 전이
  - `PARTIALLY_FILLED` order의 추가 체결을 누적
  - `CANCEL_REQUESTED` order에도 cancel-fill race condition으로 들어온 체결 반영 허용
- `OrderFillRequest` 추가
  - `fillExecutionId`, `fillQuantity`, `fillPrice`, `feeAmount`, `taxAmount`를 API 입력으로 받음
  - 요청 필드 누락/잘못된 숫자 값은 `400 Bad Request`
- `OrderFillService`의 order 미존재 예외를 `OrderNotFoundException`으로 정리
- `ExecutionExceptionHandler`에 fill 예외 매핑 추가
- fill controller 테스트 추가
- `docs/execution-api.md`에 fill API 사용법 추가
- `docs/restful-api-strategy.md`의 현재 API 목록 갱신

#### 메모

- 이번 API는 `OrderIntent 생성 -> risk 평가 -> order 변환 -> order 제출 -> ACK -> fill`까지 MVP 주문 흐름을 HTTP 레벨로 연결하는 단계다.
- `fillExecutionId`는 broker/exchange 체결 이벤트의 idempotency key 역할을 한다.
- 같은 `fillExecutionId`가 재전송되면 체결 수량을 다시 더하지 않고 현재 order를 반환한다.
- 체결 수량 누적 결과가 원 주문 수량과 같으면 `FILLED`, 작으면 `PARTIALLY_FILLED`다.
- `CANCEL_REQUESTED` 상태에서 fill을 허용하는 이유는 실제 시장에서 취소 요청과 체결 이벤트가 교차 도착할 수 있기 때문이다.
- overfill은 주문 상태와 요청 문법은 맞지만 현재 order 수량과 충돌하는 상황이므로 `409 Conflict`로 본다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 전체 빌드: `./gradlew build`

### 2026.06.05 slice

`SENT` order에 대한 broker/exchange ACK 또는 reject 이벤트를 HTTP API로 반영할 수 있도록 execution API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/orders/{orderId}/acknowledgements` 추가
  - `SENT` order를 `ACKED` 상태로 전이
  - 같은 ACK `eventId` 재요청은 중복 이벤트로 보고 현재 order 반환
- `POST /api/orders/{orderId}/rejections` 추가
  - `SENT` order를 `REJECTED` 상태로 전이
  - 같은 reject `eventId` 재요청은 중복 이벤트로 보고 현재 order 반환
- `OrderExecutionEventRequest` 추가
  - broker/exchange 이벤트 id를 API 입력으로 받음
- `ExecutionRequestException` 추가
  - `eventId` 누락은 `400 Bad Request`
- `OrderAcknowledgementService`의 order 미존재 예외를 `OrderNotFoundException`으로 정리
- `ExecutionExceptionHandler`에 ACK/REJECT 관련 예외 매핑 추가
- ACK/REJECT controller 테스트 추가
- `docs/execution-api.md`에 ACK/REJECT API 사용법 추가
- `docs/restful-api-strategy.md`의 현재 API 목록 갱신

#### 메모

- 이번 API는 `OrderIntent 생성 -> risk 평가 -> order 변환 -> order 제출 -> broker 응답 반영`까지 MVP 주문 흐름을 HTTP 레벨로 연결하는 단계다.
- `SENT`는 전송 요청 상태이고, `ACKED`는 broker/exchange가 주문을 실제로 접수했다는 상태다.
- `REJECTED`는 broker/exchange가 주문을 거절한 최종 상태에 가깝다.
- `eventId`를 API 입력으로 받는 이유는 외부 이벤트 재전송, webhook 중복 수신, 메시지 재처리 상황에서 같은 이벤트를 한 번만 반영하기 위해서다.
- ACK와 REJECT는 같은 `SENT` 주문에서 서로 배타적인 응답이므로, 같은 `eventId`가 다른 이벤트 타입으로 재사용되면 충돌로 본다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 전체 빌드: `./gradlew build`

### 2026.06.05 slice

생성된 `Order(CREATED)`를 HTTP API로 제출 처리해 `SENT` 상태로 전이할 수 있도록 execution API를 확장.

#### 이번 슬라이스에서 한 일

- `POST /api/orders/{orderId}/submissions` 추가
  - `CREATED` order를 `SENT` 상태로 전이
  - 이미 `SENT`인 order는 중복 제출 요청으로 보고 기존 order 반환
- `OrderNotFoundException` 추가
  - 존재하지 않는 order는 `404 Not Found`
  - 제출 가능한 상태가 아닌 order는 `409 Conflict`
- `ExecutionExceptionHandler`에 submission/not found 예외 매핑 추가
- submission controller 테스트 추가
- `docs/execution-api.md`에 order 제출 API 사용법 추가
- `docs/restful-api-strategy.md`의 현재 API 목록 갱신

#### 메모

- 이번 API는 `OrderIntent 생성 -> risk 평가 -> order 변환 -> order 제출`까지 MVP 주문 흐름을 HTTP 레벨로 연결하는 단계다.
- `SENT`는 broker/exchange에 전송 요청을 보낸 내부 상태이고, broker/exchange가 실제로 접수했다는 의미는 아니다.
- 실제 접수 확인은 다음 단계에서 ACK API 또는 내부 이벤트 처리로 `ACKED` 상태를 만들 때 표현한다.
- `POST /api/orders/{orderId}/submissions`는 상태를 직접 `PATCH`로 바꾸는 대신, "제출 시도"라는 도메인 동작을 별도 하위 리소스로 남기는 형태다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 전체 빌드: `./gradlew build`

### 2026.06.05 slice

사전 리스크를 통과한 `OrderIntent`를 HTTP API로 실제 `Order`로 변환할 수 있도록 execution 진입점을 추가.

#### 이번 슬라이스에서 한 일

- `POST /api/order-intents/{intentId}/orders` 추가
  - `RISK_APPROVED` intent를 `Order(CREATED)`로 변환
  - 변환 완료 후 intent는 `CONVERTED_TO_ORDER` 상태로 저장
  - 같은 intent 재요청 시 기존 order를 반환
- `ExecutionExceptionHandler` 추가
  - 존재하지 않는 intent는 `404 Not Found`
  - 변환 가능한 상태가 아닌 intent는 `409 Conflict`
- `OrderConversionService`의 intent 미존재 예외를 `OrderIntentNotFoundException`으로 정리
- execution controller 테스트 추가
- `docs/execution-api.md` 추가
- `README.md`에 execution API 문서 링크 추가

#### 메모

- 이번 API는 MVP 주문 파이프라인에서 `OrderIntent 생성 -> risk 평가 -> order 변환`까지 HTTP 레벨로 연결하는 단계다.
- `OrderIntent`는 아직 주문 후보에 가깝고, `Order`는 execution/OMS 상태 머신이 관리할 실제 주문 객체다.
- `POST /api/order-intents/{intentId}/orders`는 같은 intent 아래에 order 리소스를 만드는 의미라서 기존 `order-intents/{intentId}/evaluations` 흐름과 이어진다.
- 서비스는 `intentId` 기준으로 이미 생성된 order를 먼저 조회하므로, 클라이언트 재시도 때문에 같은 intent에서 order가 여러 개 생기는 것을 막는다.
- 아직 broker/exchange 전송 API는 열지 않았다. 다음 slice에서는 `Order(CREATED)`를 `SENT`로 넘기는 submission API를 노출하는 작업이 자연스럽다.

#### 검증

- 실행 테스트: `./gradlew :execution:test`
- 전체 빌드: `./gradlew build`

## cross-cutting

### 2026.06.02 slice

새 HTTP API를 만들 때 반복해서 참고할 수 있는 RESTful API 설계 전략 문서를 추가.

#### 이번 슬라이스에서 한 일

- `docs/restful-api-strategy.md` 추가
  - URL naming, HTTP method, status code, request/response, error response 기준 정리
  - controller를 얇게 유지하고 application service로 도메인 로직을 위임하는 기준 정리
  - idempotency key, query parameter, API 테스트, 문서화 기준 정리
  - 현재 프로젝트의 intent-generation/audit-replay API 목록을 표로 정리
- `README.md`에 RESTful API 전략 문서 링크 추가

#### 메모

- 앞으로 API를 추가할 때 endpoint 이름, status code, 에러 응답 모양을 매번 새로 고민하지 않고 같은 기준에서 출발하기 위한 문서다.
- 금융 OMS 도메인은 감사 추적과 장애 분석이 중요하므로, 단순 CRUD보다 리소스 이름과 상태 전이 의미가 명확한 API 계약을 우선한다.

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

### 2026.06.04 slice

생성된 `OrderIntent`를 API로 다시 조회할 수 있도록 query endpoint를 추가.

#### 이번 슬라이스에서 한 일

- `OrderIntentQueryService` 추가
  - `intentId`로 주문 의도 조회
  - `idempotencyKey`로 주문 의도 조회
  - 조회 결과가 없으면 `OrderIntentNotFoundException` 발생
- `GET /api/order-intents/{intentId}` 엔드포인트 추가
- `GET /api/order-intents?idempotencyKey=...` 엔드포인트 추가
- `OrderIntentExceptionHandler`에서 조회 실패를 `404 Not Found`로 응답하도록 변경
- query service 테스트와 MVC controller 테스트 추가
- `docs/order-intent-api.md`에 조회 API 사용법 추가

#### 메모

- 주문 의도 생성 API가 생겼지만 조회 API가 없으면 이후 risk 평가, order conversion, 운영 확인 흐름에서 생성된 intent를 다시 확인하기 어렵다.
- `idempotencyKey` 조회는 클라이언트가 생성 응답을 잃어버렸거나 재시도 결과를 확인해야 할 때 유용하다.
- 이번 조회 API는 MVP 파이프라인의 다음 단계인 pre-trade risk 평가와 order conversion API를 붙이기 위한 기반이다.

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`
- 실행 테스트: `./gradlew build`

### 2026.06.03 slice

주문 의도 생성 API에서 `idempotencyKey` 재전송과 key 재사용 충돌을 구분하도록 보강.

#### 이번 슬라이스에서 한 일

- `OrderIntentCreator` 추가
  - `idempotencyKey`가 있으면 repository에서 기존 `OrderIntent`를 먼저 조회
  - 같은 key와 같은 요청 내용이면 새로 생성하지 않고 기존 intent를 반환
  - 같은 key지만 요청 내용이 다르면 `OrderIntentIdempotencyConflictException` 발생
  - 기존 intent가 없으면 `OrderIntentFactory`로 생성한 뒤 저장
  - in-memory MVP 환경에서 같은 key가 동시에 들어오는 상황을 줄이기 위해 공통 생성 지점을 동기화
- `ManualOrderIntentService`, `RebalancingOrderIntentService`, `StrategyOrderIntentService`가 공통 `OrderIntentCreator`를 사용하도록 변경
- `OrderIntentIdempotencyConflictException` 추가
- `OrderIntentExceptionHandler`에서 idempotency 충돌을 `409 Conflict`로 응답하도록 변경
- idempotency key 정규화, 동일 key 재시도, 동일 key payload 충돌 테스트 추가
- `docs/order-intent-api.md`, `docs/restful-api-strategy.md`에 중복 요청/충돌 정책 반영
- `docs/order-intent-api.md`에 클라이언트가 생성할 `idempotencyKey` 권장 포맷과 예시 추가
- 같은 payload라도 다른 `idempotencyKey`면 별도 요청으로 처리하고, 중복 주문 의심은 별도 duplicate/risk rule에서 다루기로 문서화

#### 메모

- 주문 생성 계열 API는 네트워크 재시도, 브라우저 중복 클릭, worker 재처리로 같은 요청이 반복될 수 있다.
- 같은 `idempotencyKey`와 같은 요청 내용이면 정상 재시도로 보고 최초 생성 결과를 반환한다.
- 같은 `idempotencyKey`인데 주문 수량, 가격, source, 종목 등 요청 내용이 다르면 재시도가 아니라 key 충돌로 보고 `409 Conflict`를 반환한다.
- 같은 요청 내용이어도 `idempotencyKey`가 다르면 새 주문 의도 생성 요청으로 본다.
- 충돌을 조용히 기존 결과로 반환하면 호출자는 다른 주문이 생성됐다고 오해할 수 있으므로 명시적으로 실패시키는 편이 안전하다.

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`
- 실행 테스트: `./gradlew build`

#### TODO

- DB 영속화 단계에서 `idempotency_key` unique constraint 추가
- normalized request payload 기반 `request_hash` 컬럼 추가 검토
- 같은 `idempotencyKey` 재요청 시 DB transaction 안에서 기존 row를 조회하도록 변경
- unique constraint 충돌 발생 시 기존 row의 `request_hash`와 새 요청 hash 비교
  - hash가 같으면 기존 `OrderIntent` 반환
  - hash가 다르면 `409 Conflict` 반환
- DB 기반 idempotency가 들어오면 `OrderIntentCreator.create()`의 JVM-local `synchronized` 의존 제거 검토
- 같은 payload와 다른 `idempotencyKey`가 짧은 시간 안에 반복되는 경우를 탐지하는 duplicate order rule 검토
- rebalancing/strategy는 `sourceRefId` 기준 중복 주문 방어 정책 검토

### 2026.06.03 slice

수동/리밸런싱/전략 주문 의도 생성 API를 항상 참고할 수 있도록 별도 사용 가이드로 정리.

#### 이번 슬라이스에서 한 일

- `docs/order-intent-api.md` 추가
  - `POST /api/order-intents/manual`
  - `POST /api/order-intents/rebalancing`
  - `POST /api/order-intents/strategy`
- source별 request/response 예시 추가
- `sourceType`과 `sourceRefId`가 어떤 추적 의미를 갖는지 정리
- request/response field reference와 대표 검증 오류 정리
- `README.md`, `intent-generation/README.md`에 문서 링크 추가

#### 메모

- 주문 의도 API는 MVP의 첫 입력 지점이므로, API 계약을 코드만 보고 추론하지 않도록 문서로 고정했다.
- manual/rebalancing/strategy는 서로 다른 source를 갖지만 생성 후에는 모두 공통 `OrderIntent` 파이프라인으로 들어간다.
- 이후 `OrderIntent` 조회 API나 error response의 `code/path/occurredAt` 확장을 추가할 때 이 문서를 함께 확장하면 된다.

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

### 2026.06.02 slice

리밸런싱/전략 기반 주문 의도 생성 흐름을 HTTP API로 열어 MVP의 주문 소스 3종을 같은 API 레벨에서 다룰 수 있게 함.

#### 이번 슬라이스에서 한 일

- `POST /api/order-intents/rebalancing` 엔드포인트 추가
  - `RebalancingOrderIntentRequest`를 받아 `OrderIntentSourceType.REBALANCING` 주문 의도를 생성
  - `rebalanceRunId`를 `sourceRefId`로 보존해 어떤 리밸런싱 실행에서 나온 주문인지 추적 가능하게 함
- `POST /api/order-intents/strategy` 엔드포인트 추가
  - `StrategyOrderIntentRequest`를 받아 `OrderIntentSourceType.STRATEGY` 주문 의도를 생성
  - `strategySignalId`를 `sourceRefId`로 보존해 어떤 전략 신호에서 나온 주문인지 추적 가능하게 함
- rebalancing/strategy controller 테스트 추가
  - 정상 생성 시 `201 Created`와 생성된 `OrderIntent` JSON을 반환하는지 검증
  - 생성 규칙 위반 시 `OrderIntentExceptionHandler`를 통해 `400 Bad Request`로 변환되는지 검증

#### 메모

- README의 MVP 범위는 수동 주문, 리밸런싱 주문, 전략 신호 주문을 모두 주문 의도 입력 소스로 둔다.
- 이전에는 manual만 HTTP API가 있고 rebalancing/strategy는 service 진입점만 있어 외부 호출 관점에서는 주문 소스가 비대칭이었다.
- 이번 변경으로 세 주문 소스 모두 `OrderIntentFactory -> OrderIntentRepository`의 공통 생성/저장 경로를 사용한다.
- `sourceType`은 주문이 어느 시스템 흐름에서 왔는지 구분하고, `sourceRefId`는 해당 흐름의 원본 실행 ID나 신호 ID를 감사/추적용으로 남기는 역할을 한다.

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`
- 실행 테스트: `./gradlew build`

#### 다음 후보

- 주문 의도 생성 API 문서화
- 생성된 intent를 pre-trade risk 평가 API로 넘기는 통합 흐름 정리
- idempotency key 기반 중복 생성 방어를 API 레벨에서 명확히 검증

## pre-trade-risk

### 2026.06.05 slice

생성된 `OrderIntent`를 HTTP API로 pre-trade risk 평가하고, 평가 결과에 따라 intent 상태를 전이할 수 있도록 진입점을 추가.

#### 이번 슬라이스에서 한 일

- `pre-trade-risk` 모듈에 Spring Web/test web 의존성 추가
- `PreTradeRiskEvaluationRequest` 추가
  - limit, exposure, open order, market, control context를 API request에서 받을 수 있도록 구성
- `POST /api/pre-trade-risk/order-intents/{intentId}/evaluations` 엔드포인트 추가
  - `OrderIntentQueryService`로 평가 대상 intent 조회
  - `PreTradeRiskOrderIntentService`로 risk 평가 및 `RISK_APPROVED` / `RISK_REJECTED` 상태 전이 저장
- `PreTradeRiskExceptionHandler` 추가
  - 없는 intent는 `404 Not Found`
  - `CREATED` 상태가 아닌 intent 평가 시도는 `409 Conflict`
- request context 변환 테스트와 MVC controller 테스트 추가
- `docs/pre-trade-risk-api.md` 추가
- `README.md`, `pre-trade-risk/README.md`에 API 문서 링크 추가

#### 메모

- 이 API는 MVP 파이프라인에서 `OrderIntent 생성 -> 조회 -> risk 평가` 흐름을 HTTP 레벨에서 연결하는 단계다.
- 실제 limit/exposure/open order/market/control 조회는 아직 API 내부에서 수행하지 않고, 호출자가 request body로 전달한 context를 평가한다.
- 이후 작업에서는 운영 화면이나 orchestration layer가 필요한 context를 조회해 이 API를 호출하거나, risk 모듈 내부에 context query adapter를 붙이는 방향을 검토할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`
- 실행 테스트: `./gradlew build`

### 2026.06.03 slice

`idempotencyKey`가 다른 동일 payload 주문 의심 케이스를 pre-trade risk의 duplicate open order 규칙에서 추적하기 쉽게 보강.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskOpenOrderContext`에 `duplicateOpenOrderId` 추가
  - 기존 `duplicateOpenOrderExists` Boolean 생성자는 유지해 기존 호출부 호환
- `DuplicateOpenOrderRule`이 중복 open order id를 받으면 rule result의 `evaluatedValue`에 기록하도록 변경
- duplicate open order id가 평가 결과에 남는 테스트 추가
- `pre-trade-risk/README.md`에 idempotency와 duplicate open order rule의 책임 차이 정리

#### 메모

- `idempotencyKey`는 같은 요청 재시도를 묶는 API 생성 단계 장치다.
- 같은 payload라도 다른 `idempotencyKey`가 들어오면 별도 요청으로 처리될 수 있다.
- 이런 요청이 실제 중복 주문인지 여부는 open order 조회 결과를 바탕으로 pre-trade risk에서 판단한다.
- 현재는 저장소 조회를 직접 붙이지 않고, 호출자가 조회한 duplicate 여부와 매칭된 open order id를 context로 전달하는 계약을 고정한다.

#### 검증

- 실행 테스트: `./gradlew :pre-trade-risk:test`
- 실행 테스트: `./gradlew build`

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

### 2026.05.30 slice

현재 저장된 order row와 이벤트 기반 replay 결과를 비교하는 replay consistency check 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `OrderReplayConsistencyResult` 추가
- `OrderReplayConsistencyService` 추가
  - `OrderRepository`에서 현재 order row 조회
  - `OrderExecutionReplayService`로 이벤트 기반 상태/체결 수량 재현
  - 실제 order status와 replay status 비교
  - 실제 filled quantity와 replay filled quantity 비교
  - 상태와 체결 수량이 모두 같으면 consistent로 판단
- 일치 케이스, 상태 불일치 케이스, 체결 수량 불일치 케이스, missing order 테스트 추가

#### 메모

- replay 자체는 이벤트 로그만으로 "이 주문이 어떤 상태가 되었어야 하는가"를 계산한다.
- consistency check는 그 replay 결과를 현재 저장된 order row와 대조해 실제 데이터가 이벤트 로그와 맞는지 확인하는 단계다.
- 운영 장애에서 중요한 질문은 단순히 "이벤트를 재생할 수 있는가"가 아니라 "현재 DB 상태가 이벤트 기반 재현 결과와 일치하는가"이다.
- 예를 들어 이벤트 로그상으로는 두 번의 fill이 있어 `FILLED / 10주`가 되어야 하는데 order row가 `PARTIALLY_FILLED / 10주`로 남아 있다면 상태 전이 저장이 누락됐을 가능성이 있다.
- 반대로 상태는 같지만 filled quantity가 다르면 fill event 누적 처리나 중복 처리에 문제가 있었는지 확인해야 한다.
- 이번 slice는 reconciliation 결과를 boolean과 비교 필드로 반환하는 기본 형태이며, 이후 운영 화면이나 알림/복구 플로우에서 사용할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.30 slice

replay consistency check 결과에 불일치 사유 코드를 추가해 운영자가 차이를 바로 분류할 수 있도록 보강.

#### 이번 슬라이스에서 한 일

- `OrderReplayMismatchReason` 추가
  - `STATUS_MISMATCH`
  - `FILLED_QUANTITY_MISMATCH`
- `OrderReplayConsistencyResult`에 `mismatchReasons` 추가
- `OrderReplayConsistencyService`가 상태/체결 수량 비교 결과를 reason list로 변환하도록 변경
- 일치 케이스에서는 빈 reason list를 반환
- 상태 불일치, 체결 수량 불일치, 두 값이 모두 다른 케이스 테스트 추가

#### 메모

- 이전 consistency result는 `consistent = false`와 실제/replay 값을 함께 반환했지만, 불일치 원인은 결과를 읽는 쪽에서 다시 해석해야 했다.
- 운영 화면, 알림, 복구 배치에서는 "불일치가 있다"보다 "어떤 종류의 불일치인가"가 더 중요하다.
- 예를 들어 `STATUS_MISMATCH`는 상태 전이 저장 누락이나 잘못된 상태 갱신을 먼저 의심하게 하고, `FILLED_QUANTITY_MISMATCH`는 fill 누락/중복 처리나 체결 수량 누적 로직을 먼저 확인하게 한다.
- reason code를 결과에 포함하면 API 응답, 대시보드 필터, 경보 라우팅, 자동 복구 후보 분류에서 같은 판단 로직을 중복 구현하지 않아도 된다.
- `consistent`는 reason list가 비어 있는지로 계산되므로, 이후 mismatch 종류가 추가되어도 일관된 방식으로 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.31 slice

저장된 모든 order를 대상으로 replay consistency를 한 번에 점검하는 report 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `OrderRepository.findAll()` 추가
- `InMemoryOrderRepository.findAll()` 구현
  - `createdAt`, `orderId` 기준으로 정렬해 안정적인 조회 순서 제공
- `OrderReplayConsistencyReport` 추가
  - 전체 주문 수
  - 일치 주문 수
  - 불일치 주문 수
  - 개별 `OrderReplayConsistencyResult` 목록
  - report 점검 시각
- `OrderReplayConsistencyReportService` 추가
  - 저장된 모든 order row를 조회
  - 각 order를 `OrderReplayConsistencyService`로 검증
  - consistency 결과를 집계 report로 반환
- 전체 주문 report, 빈 주문 report, repository 전체 조회 정렬 테스트 추가

#### 메모

- 단건 consistency check는 특정 주문의 상태가 이벤트 기반 replay 결과와 맞는지 확인하는 도구다.
- 운영 관점에서는 단건 확인만으로는 부족하고, 일정 주기로 전체 주문을 훑어 "현재 시스템에 불일치가 몇 건 있는가"를 집계할 수 있어야 한다.
- report 결과에 `consistentCount`와 `inconsistentCount`를 포함하면 배치 작업, 운영 대시보드, 알림 조건에서 전체 상태를 빠르게 판단할 수 있다.
- 개별 결과 목록에는 기존 `mismatchReasons`가 그대로 들어가므로, 불일치 주문을 다시 조회하지 않아도 상태 불일치인지 체결 수량 불일치인지 분류할 수 있다.
- `OrderRepository.findAll()`은 이후 DB/JPA로 전환될 때 pagination이나 조건 조회로 확장될 가능성이 있지만, 현재 MVP 단계에서는 in-memory 기반 전체 점검 계약을 먼저 고정했다.
- 조회 순서를 고정해두면 report 테스트가 안정적이고, 운영 로그에서도 같은 데이터에 대해 결과 순서가 흔들리지 않는다.

#### 검증

- 실행 테스트: `./gradlew :execution:test :audit-replay:test`

### 2026.05.31 slice

replay consistency report에서 불일치 주문만 결과 목록으로 받을 수 있는 필터 흐름을 추가.

#### 이번 슬라이스에서 한 일

- `OrderReplayConsistencyReportService.checkInconsistentOnly()` 추가
  - 전체 order row를 모두 점검
  - 집계 수치는 전체 점검 결과 기준으로 계산
  - `results` 목록에는 불일치 주문만 포함
- 전체 점검 로직을 내부 `checkOrders()`로 분리
- report 생성 로직을 내부 `toReport()`로 분리
- 불일치 결과만 반환하는 케이스 테스트 추가

#### 메모

- 전체 consistency report는 시스템 상태를 넓게 보는 데 좋지만, 운영자가 실제로 조치해야 하는 대상은 불일치 주문이다.
- `checkInconsistentOnly()`는 전체 주문을 검사하되 결과 목록을 불일치 주문으로 줄여, 운영 화면이나 알림 payload가 바로 조치 대상 중심으로 구성되게 한다.
- 집계 값은 전체 점검 기준으로 유지한다.
  - `totalCount`는 검사한 전체 주문 수
  - `consistentCount`는 일치 주문 수
  - `inconsistentCount`는 불일치 주문 수
  - `results`는 불일치 주문 상세 목록
- 이 구조는 “전체적으로 몇 건을 봤고, 그중 몇 건이 문제이며, 실제로 봐야 할 주문은 무엇인가”를 한 응답에서 같이 제공한다.
- 이후 HTTP API를 붙일 때 `all`과 `inconsistent-only` 조회를 query parameter로 나누거나, 별도 endpoint로 노출하기 쉽다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.31 slice

replay consistency report를 HTTP API로 조회할 수 있도록 audit-replay 진입점을 추가.

#### 이번 슬라이스에서 한 일

- `audit-replay` 모듈에 web 의존성 추가
- `OrderReplayConsistencyReportController` 추가
  - `GET /api/audit-replay/order-replay/consistency-report`
  - 기본 조회는 전체 consistency report 반환
  - `inconsistentOnly=true` query parameter로 불일치 주문 중심 report 반환
- 전체 report API 응답 테스트 추가
- 불일치 주문만 조회하는 API 응답 테스트 추가

#### 메모

- 이전 slice까지는 replay consistency report가 service 레벨에서만 제공됐다.
- 이번 slice부터는 운영자 도구, 배치 모니터링, 간단한 curl 점검에서 report를 HTTP로 직접 조회할 수 있다.
- 기본 API는 전체 주문 점검 결과를 반환하므로 시스템 전체의 consistency 상태를 확인하는 데 적합하다.
- `inconsistentOnly=true`는 조치 대상만 빠르게 보고 싶을 때 사용한다.
- 두 응답 모두 같은 `OrderReplayConsistencyReport` 모델을 사용하므로, 클라이언트는 집계 필드와 개별 결과 필드를 동일한 구조로 처리할 수 있다.
- 아직 인증/권한/페이지네이션은 붙이지 않았다. MVP 단계에서는 내부 운영용 조회 진입점을 먼저 고정하고, 이후 DB/JPA 전환이나 운영 UI 연결 시 조건 조회를 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.31 slice

특정 order의 execution replay 결과를 HTTP API로 조회할 수 있는 단건 진입점을 추가.

#### 이번 슬라이스에서 한 일

- `OrderExecutionReplayController` 추가
  - `GET /api/audit-replay/order-replay/{orderId}`
  - `orderQuantity` query parameter로 원 주문 수량 입력
  - `OrderExecutionReplayService.replay(orderId, orderQuantity)` 결과 반환
- 단건 replay API 응답 테스트 추가

#### 메모

- consistency report API는 전체 주문을 훑어 현재 시스템에 불일치가 있는지 확인하는 용도다.
- 단건 replay API는 특정 주문을 깊게 분석할 때 사용한다.
- 예를 들어 report에서 어떤 order가 `STATUS_MISMATCH`로 표시되면, 운영자는 해당 order id와 원 주문 수량으로 단건 replay API를 호출해 이벤트 기반 재현 상태를 직접 확인할 수 있다.
- 응답에는 시작 상태, replay된 최종 상태, 원 주문 수량, replay 누적 체결 수량, 적용 이벤트 수, replay 시각이 포함된다.
- 현재 API는 `orderQuantity`를 query parameter로 받는다. 이후 order 조회와 replay를 결합하는 API가 생기면 클라이언트가 원 주문 수량을 넘기지 않아도 되도록 확장할 수 있다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.05.31 slice

audit-replay 진단 API를 확장해 특정 주문의 audit trail과 consistency 결과를 HTTP로 직접 조회할 수 있도록 보강.

#### 이번 슬라이스에서 한 일

- `AuditReplayErrorResponse` 추가
- `AuditReplayExceptionHandler` 추가
  - `OrderReplayException`, `IllegalArgumentException`을 `400 Bad Request` JSON 응답으로 변환
  - 누락된 query parameter를 `{ "message": "<parameter> is required" }` 형태로 반환
  - UUID/path/query parameter 변환 실패를 `invalid request argument` 메시지로 반환
  - 다른 모듈의 controller 예외 처리와 충돌하지 않도록 `com.multiassetoms.auditreplay` 패키지로 적용 범위 제한
- `audit-replay` 모듈에 servlet API compile-only 의존성 추가
  - servlet 기반 MVC 예외 타입을 API 계층에서 컴파일할 수 있도록 보강
- `OrderAuditTrailController` 추가
  - `GET /api/audit-replay/order-audit-trails/{orderId}`
  - 특정 order의 execution event와 fill event timeline 반환
- `OrderReplayConsistencyController` 추가
  - `GET /api/audit-replay/order-replay/consistency/{orderId}`
  - 현재 order row와 replay 결과의 단건 consistency check 결과 반환
- audit trail API 응답 테스트 추가
- consistency API 응답/오류 테스트 추가
- replay API의 missing `orderQuantity` 오류 응답 테스트 추가

#### 메모

- 이전 API들은 전체 consistency report와 단건 replay 결과를 제공했다.
- 이번 slice는 운영자가 불일치 주문을 발견한 뒤 원인을 따라갈 수 있는 진단 경로를 더 완성한다.
- 기본적인 조사 흐름은 다음과 같다.
  - 전체 report API로 불일치 주문 발견
  - 단건 consistency API로 실제 order row와 replay 결과 차이 확인
  - 단건 replay API로 이벤트 기반 최종 상태 재현
  - audit trail API로 어떤 execution/fill 이벤트가 어떤 순서로 적용됐는지 확인
- audit trail API는 이벤트 원본에 가까운 timeline을 보여주므로, 상태 불일치가 ACK/REJECT/CANCEL 이벤트 문제인지 fill 누적 문제인지 추적하는 데 사용된다.
- API 오류 응답을 JSON 형태로 통일하면 운영 UI나 curl 점검에서 실패 원인을 사람이 바로 읽을 수 있고, 이후 클라이언트 쪽 오류 처리도 단순해진다.
- 아직 인증/권한/감사 조회 권한은 붙이지 않았다. MVP 단계에서는 내부 운영용 진단 API 표면을 먼저 고정했다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.06.01 slice

audit-replay 운영 진단 API의 사용 흐름과 응답 계약을 문서로 정리.

#### 이번 슬라이스에서 한 일

- `docs/audit-replay-api.md` 추가
  - 전체 consistency report API 정리
  - 단건 consistency API 정리
  - 단건 execution replay API 정리
  - order audit trail API 정리
  - 오류 응답 형태와 대표 오류 케이스 정리
  - 운영자가 불일치 주문을 조사할 때 권장하는 진단 순서 정리
  - curl 예시 추가
- `README.md`에 audit-replay API guide 링크 추가

#### 메모

- audit-replay API가 여러 개로 늘어나면서 어떤 API를 언제 호출해야 하는지 문서화가 필요해졌다.
- 전체 report는 불일치 주문을 찾는 용도이고, 단건 consistency/replay/audit trail API는 특정 주문을 깊게 추적하는 용도다.
- 문서에 권장 조사 흐름을 남겨두면 이후 운영 UI나 배치 모니터링을 붙일 때도 API 역할을 혼동하지 않는다.
- 아직 OpenAPI/Swagger 문서는 아니지만, MVP 단계에서는 사람이 읽을 수 있는 markdown 계약 문서로 API 표면을 먼저 고정했다.

#### 검증

- 문서 변경만 수행

### 2026.06.01 slice

저장된 order row의 원 주문 수량을 사용해 execution replay를 수행하는 API를 추가.

#### 이번 슬라이스에서 한 일

- `OrderExecutionReplayQueryService` 추가
  - `OrderRepository`에서 현재 order row 조회
  - 저장된 `Order.quantity`를 원 주문 수량으로 사용해 `OrderExecutionReplayService.replay(...)` 호출
  - order가 없으면 `OrderReplayException("order not found")` 반환
- `OrderExecutionReplayController`에 저장 주문 기반 replay endpoint 추가
  - `GET /api/audit-replay/order-replay/stored-orders/{orderId}`
- 저장 주문 기반 replay service 테스트 추가
- 저장 주문 기반 replay controller 테스트 추가
- `docs/audit-replay-api.md`에 새 endpoint와 기존 explicit quantity replay API의 차이 반영

#### 메모

- 기존 단건 replay API는 `orderQuantity`를 query parameter로 직접 받아야 했다.
- 운영자가 불일치 주문을 조사할 때 매번 원 주문 수량을 별도로 찾아 넣는 것은 번거롭고 실수 가능성이 있다.
- 저장 주문 기반 replay API는 order id만으로 현재 저장된 order row를 찾고, 그 안의 원 주문 수량을 replay 기준 수량으로 사용한다.
- 기존 `orderQuantity` 직접 입력 API는 테스트성 호출이나 방어 로직 검증을 위해 유지한다.
- 일반적인 운영 조사 흐름에서는 저장 주문 기반 replay API를 우선 사용하고, 특별히 다른 수량으로 replay를 실험해야 할 때 explicit quantity API를 사용하면 된다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.06.01 slice

order replay consistency 흐름을 query service와 계산 service로 분리.

#### 이번 슬라이스에서 한 일

- `OrderReplayConsistencyQueryService` 추가
  - `OrderRepository`에서 저장된 order row 조회
  - 저장된 order quantity로 `OrderExecutionReplayService` 실행
  - 실제 order snapshot과 replay 결과를 `OrderReplayConsistencyService`에 전달
- `OrderReplayConsistencyService` 책임 축소
  - repository 조회 제거
  - replay 실행 제거
  - 이미 준비된 order snapshot과 replay result 비교만 담당
- `OrderReplayConsistencyController`가 query service를 사용하도록 변경
- `OrderReplayConsistencyReportService`가 전체 order 목록을 순회하면서 query service로 단건 consistency를 계산하도록 변경
- consistency service 테스트를 순수 비교 테스트로 재정리
- consistency query service 테스트 추가
- `docs/audit-replay-api.md`에 query service와 계산 service 책임 분리 메모 추가

#### 메모

- 기존 `OrderReplayConsistencyService`는 order 조회, replay 실행, mismatch 판단을 모두 담당했다.
- 저장소 조회와 계산 규칙이 한 클래스에 섞이면 테스트 범위가 커지고, 이후 replay 입력원이 바뀔 때 핵심 mismatch 판단 로직까지 흔들릴 수 있다.
- 이번 변경으로 query service는 API/report 조회에 필요한 입력 조립을 맡고, consistency service는 실제 값과 replay 값을 비교하는 계산 규칙만 담당한다.
- 현업에서도 core 계산 로직과 DB 조회 기반 query/application service를 나눠 책임 경계를 선명하게 두는 경우가 많다.

#### 검증

- 실행 테스트: `./gradlew :audit-replay:test`

### 2026.06.02 slice

replay consistency report에 불일치 비율 필드를 추가.

#### 이번 슬라이스에서 한 일

- `OrderReplayConsistencyReport.inconsistentRatio` 추가
- `OrderReplayConsistencyReportService`에서 불일치 비율 계산
  - `inconsistentCount / totalCount`
  - 소수점 4자리, `HALF_UP` 반올림
  - 주문이 없으면 `0.0000`
- report service 테스트에 비율 검증 추가
- report controller 테스트에 JSON 응답 비율 검증 추가
- `docs/audit-replay-api.md`에 `inconsistentRatio` 응답 필드 설명 추가

#### 메모

- count만으로도 불일치 규모를 알 수 있지만, 운영 화면이나 알림 조건에서는 전체 대비 비율이 더 직관적이다.
- 예를 들어 `inconsistentCount = 2`라도 전체가 3건이면 심각하고, 전체가 10,000건이면 다른 판단이 필요하다.
- `inconsistentRatio`를 API 응답에 포함하면 클라이언트마다 같은 비율 계산을 반복하지 않아도 된다.
- 0건 report에서는 분모가 0이므로 `0.0000`으로 고정해 응답 계약을 단순하게 유지한다.

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
