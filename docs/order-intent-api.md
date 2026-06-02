# Order Intent API Guide

`intent-generation` API는 수동 주문, 리밸런싱 주문, 전략 신호 주문을 공통 `OrderIntent`로 생성하는 진입점이다.

`OrderIntent`는 아직 거래소나 브로커로 나간 실제 주문이 아니라, "무엇을 얼마나 사고팔고 싶은가"를 표현하는 주문 의도다. 이후 pre-trade risk, OMS order conversion, execution 흐름으로 이어진다.

현재 API는 세 가지 주문 소스를 제공한다.

```text
1. 수동 주문 의도 생성
2. 리밸런싱 주문 의도 생성
3. 전략 신호 주문 의도 생성
```

## 공통 흐름

세 API는 입력 source만 다르고 내부 생성 흐름은 같다.

```text
HTTP request
    -> source별 request DTO
    -> source별 service
    -> CreateOrderIntentCommand
    -> OrderIntentFactory
    -> OrderIntentRepository
    -> OrderIntent response
```

공통 생성 규칙은 `OrderIntentFactory`에서 관리한다.

| Rule | Meaning |
| --- | --- |
| `requestedQty > 0` | 주문 수량은 0보다 커야 한다. |
| `LIMIT` order | `limitPrice`가 반드시 있어야 한다. |
| `MARKET` order | `limitPrice`가 없어야 한다. |
| missing `idempotencyKey` | 기본 UUID 문자열을 생성한다. |
| initial `status` | 생성 직후 상태는 `CREATED`다. |

## Source 구분

`sourceType`은 주문 의도가 어디서 왔는지 나타낸다.

| Source | Endpoint | `sourceType` | `sourceRefId` |
| --- | --- | --- | --- |
| 수동 주문 | `POST /api/order-intents/manual` | `MANUAL` | `null` |
| 리밸런싱 주문 | `POST /api/order-intents/rebalancing` | `REBALANCING` | `rebalanceRunId` |
| 전략 신호 주문 | `POST /api/order-intents/strategy` | `STRATEGY` | `strategySignalId` |

`sourceRefId`는 원본 흐름을 추적하기 위한 참조 ID다.

예를 들어 리밸런싱 주문 의도에서 `sourceRefId=rebalance-run-20260603-001`이면, 이후 주문/체결/감사 로그를 보다가도 어떤 리밸런싱 실행에서 출발했는지 되짚을 수 있다.

## 1. 수동 주문 의도 생성

운영자나 사용자가 직접 입력한 주문 의도를 생성한다.

```http
POST /api/order-intents/manual
Content-Type: application/json
```

### Request

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "requestedQty": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "reason": "operator order",
  "idempotencyKey": "manual-key-1",
  "createdBy": "operator"
}
```

### Response

```http
201 Created
```

```json
{
  "intentId": "00000000-0000-0000-0000-000000000001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "sourceType": "MANUAL",
  "sourceRefId": null,
  "side": "BUY",
  "orderType": "LIMIT",
  "requestedQty": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "reason": "operator order",
  "status": "CREATED",
  "idempotencyKey": "manual-key-1",
  "createdBy": "operator",
  "createdAt": "2026-06-03T00:00:00Z",
  "updatedAt": "2026-06-03T00:00:00Z"
}
```

## 2. 리밸런싱 주문 의도 생성

포트폴리오 목표 비중과 현재 비중의 차이를 맞추기 위해 생성된 주문 의도를 등록한다.

```http
POST /api/order-intents/rebalancing
Content-Type: application/json
```

### Request

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "rebalanceRunId": "rebalance-run-1",
  "side": "BUY",
  "orderType": "LIMIT",
  "requestedQty": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "reason": "rebalance drift",
  "idempotencyKey": "rebalance-key-1",
  "createdBy": "rebalancer"
}
```

### Response

```http
201 Created
```

```json
{
  "intentId": "00000000-0000-0000-0000-000000024001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "sourceType": "REBALANCING",
  "sourceRefId": "rebalance-run-1",
  "side": "BUY",
  "orderType": "LIMIT",
  "requestedQty": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "reason": "rebalance drift",
  "status": "CREATED",
  "idempotencyKey": "rebalance-key-1",
  "createdBy": "rebalancer",
  "createdAt": "2026-06-03T00:00:00Z",
  "updatedAt": "2026-06-03T00:00:00Z"
}
```

## 3. 전략 신호 주문 의도 생성

자동매매 전략이 만든 신호를 주문 의도로 등록한다.

```http
POST /api/order-intents/strategy
Content-Type: application/json
```

### Request

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "strategySignalId": "signal-1",
  "side": "SELL",
  "orderType": "MARKET",
  "requestedQty": 5,
  "limitPrice": null,
  "timeInForce": "DAY",
  "reason": "momentum signal",
  "idempotencyKey": "strategy-key-1",
  "createdBy": "strategy-engine"
}
```

### Response

```http
201 Created
```

```json
{
  "intentId": "00000000-0000-0000-0000-000000025001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "sourceType": "STRATEGY",
  "sourceRefId": "signal-1",
  "side": "SELL",
  "orderType": "MARKET",
  "requestedQty": 5,
  "limitPrice": null,
  "timeInForce": "DAY",
  "reason": "momentum signal",
  "status": "CREATED",
  "idempotencyKey": "strategy-key-1",
  "createdBy": "strategy-engine",
  "createdAt": "2026-06-03T00:00:00Z",
  "updatedAt": "2026-06-03T00:00:00Z"
}
```

## Field Reference

### Request fields

| Field | Required | Meaning |
| --- | --- | --- |
| `portfolioId` | yes | 주문 의도가 속한 포트폴리오 ID |
| `instrumentId` | yes | 종목/상품 ID |
| `rebalanceRunId` | rebalancing only | 리밸런싱 실행 ID. 응답의 `sourceRefId`가 된다. |
| `strategySignalId` | strategy only | 전략 신호 ID. 응답의 `sourceRefId`가 된다. |
| `side` | yes | `BUY` 또는 `SELL` |
| `orderType` | yes | `MARKET` 또는 `LIMIT` |
| `requestedQty` | yes | 요청 수량. 0보다 커야 한다. |
| `limitPrice` | conditional | `LIMIT`이면 필수, `MARKET`이면 `null` |
| `timeInForce` | yes | 주문 유효 조건. 현재 MVP에서는 `DAY`를 주로 사용한다. |
| `reason` | no | 주문 의도 생성 사유 |
| `idempotencyKey` | no | 중복 생성 방지용 키. 비어 있으면 서버가 생성한다. |
| `createdBy` | yes | 생성 주체 |

### Response fields

| Field | Meaning |
| --- | --- |
| `intentId` | 주문 의도 식별자 |
| `sourceType` | `MANUAL`, `REBALANCING`, `STRATEGY` |
| `sourceRefId` | source별 원본 참조 ID |
| `status` | 생성 직후 `CREATED` |
| `createdAt` | 주문 의도 생성 시각 |
| `updatedAt` | 주문 의도 마지막 변경 시각 |

## Error Response

검증 실패는 `400 Bad Request`로 응답한다.

```http
400 Bad Request
```

```json
{
  "message": "limitPrice is required for LIMIT orders"
}
```

대표 오류:

| Case | Message |
| --- | --- |
| `LIMIT`인데 `limitPrice`가 없음 | `limitPrice is required for LIMIT orders` |
| `MARKET`인데 `limitPrice`가 있음 | `limitPrice must be null for MARKET orders` |
| `requestedQty <= 0` | `requestedQty must be greater than zero` |

## 다음 연결 지점

생성된 `OrderIntent`는 이후 흐름에서 다음 단계로 전달된다.

```text
OrderIntent(CREATED)
    -> Pre-Trade Risk
    -> OrderIntent(RISK_APPROVED or RISK_REJECTED)
    -> Order Conversion
    -> Execution
    -> Post-Trade
    -> Audit / Replay
```

API 관점에서 다음 확장 후보는 다음과 같다.

- `GET /api/order-intents/{intentId}` 조회 API
- `GET /api/order-intents?idempotencyKey=...` 조회 API
- 생성 API의 idempotency key 중복 요청 방어
- pre-trade risk 평가 API와 주문 의도 생성 API의 연결 문서화
