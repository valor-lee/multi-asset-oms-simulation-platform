# Execution API Guide

`execution` API는 사전 리스크를 통과한 `OrderIntent`를 실제 OMS가 관리할 `Order`로 전환하는 진입점이다.

현재 MVP 흐름에서는 다음 순서로 사용한다.

```text
OrderIntent(CREATED)
    -> pre-trade risk 평가
    -> OrderIntent(RISK_APPROVED)
    -> order 변환
    -> Order(CREATED), OrderIntent(CONVERTED_TO_ORDER)
    -> order 제출
    -> Order(SENT)
    -> broker/exchange ACK 또는 REJECT 반영
    -> Order(ACKED or REJECTED)
    -> broker/exchange fill 반영
    -> Order(PARTIALLY_FILLED or FILLED)
    -> cancel request 또는 cancel confirmation 반영
    -> Order(CANCEL_REQUESTED or CANCELED)
```

## 1. Order Intent를 Order로 변환

```http
POST /api/order-intents/{intentId}/orders
```

### Request

request body는 없다. 변환 대상은 path variable의 `intentId`로 식별한다.

### Response

```json
{
  "order": {
    "orderId": "00000000-0000-0000-0000-000000032001",
    "intentId": "00000000-0000-0000-0000-000000031001",
    "portfolioId": "portfolio-1",
    "instrumentId": "005930",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10,
    "filledQuantity": 0,
    "limitPrice": 55000,
    "timeInForce": "DAY",
    "status": "CREATED",
    "createdAt": "2026-06-05T00:00:00Z",
    "updatedAt": "2026-06-05T00:00:00Z"
  },
  "intent": {
    "intentId": "00000000-0000-0000-0000-000000031001",
    "portfolioId": "portfolio-1",
    "instrumentId": "005930",
    "sourceType": "MANUAL",
    "sourceRefId": null,
    "side": "BUY",
    "orderType": "LIMIT",
    "requestedQty": 10,
    "limitPrice": 55000,
    "timeInForce": "DAY",
    "reason": "manual order",
    "status": "CONVERTED_TO_ORDER",
    "idempotencyKey": "manual-key-1",
    "createdBy": "operator",
    "createdAt": "2026-06-05T00:00:00Z",
    "updatedAt": "2026-06-05T00:00:00Z"
  }
}
```

## 상태 규칙

| Intent status | 처리 |
| --- | --- |
| `RISK_APPROVED` | 새 `Order(CREATED)`를 만들고 intent를 `CONVERTED_TO_ORDER`로 저장 |
| `CONVERTED_TO_ORDER` | 이미 생성된 order를 반환 |
| 그 외 상태 | `409 Conflict` |

같은 `intentId`로 이미 order가 생성되어 있으면 새 order를 만들지 않는다. 네트워크 재시도나 클라이언트 중복 호출이 들어와도 같은 intent에서는 하나의 order만 유지하기 위한 방어다.

## Error Response

존재하지 않는 intent를 변환하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order intent not found"
}
```

`RISK_APPROVED`가 아닌 intent를 변환하려고 하면 `409 Conflict`를 반환한다.

```json
{
  "message": "only RISK_APPROVED order intents can be converted to orders"
}
```

## 운영 메모

- `OrderIntent`는 주문을 만들기 전 의도이고, `Order`는 OMS/execution 상태 머신이 관리하는 실제 주문 객체다.
- `Order(CREATED)`는 아직 broker/exchange로 전송된 상태가 아니다.
- 다음 단계에서는 생성된 order를 submission API로 `SENT` 상태로 넘긴다.

## 2. Duplicate Open Order 조회

```http
GET /api/orders/duplicate-open-order?portfolioId=portfolio-1&instrumentId=005930&side=BUY&orderType=LIMIT&quantity=10&limitPrice=55000&timeInForce=DAY
```

### Query Parameters

| Name | Required | Description |
| --- | --- | --- |
| `portfolioId` | yes | 주문 포트폴리오 ID |
| `instrumentId` | yes | 주문 대상 instrument ID |
| `side` | yes | `BUY` 또는 `SELL` |
| `orderType` | yes | `MARKET` 또는 `LIMIT` |
| `quantity` | yes | 주문 수량. 0보다 커야 한다. |
| `limitPrice` | LIMIT only | 지정가 주문 가격 |
| `timeInForce` | yes | 주문 유효 조건 |
| `excludeIntentId` | no | 특정 intent에서 생성된 order를 후보에서 제외할 때 사용 |

### Response

중복으로 의심되는 open order가 있으면 다음처럼 응답한다.

```json
{
  "duplicateOpenOrderExists": true,
  "duplicateOpenOrderId": "00000000-0000-0000-0000-000000062001",
  "duplicateOpenOrder": {
    "orderId": "00000000-0000-0000-0000-000000062001",
    "intentId": "00000000-0000-0000-0000-000000063001",
    "portfolioId": "portfolio-1",
    "instrumentId": "005930",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10,
    "filledQuantity": 0,
    "limitPrice": 55000,
    "timeInForce": "DAY",
    "status": "ACKED",
    "createdAt": "2026-06-20T00:00:00Z",
    "updatedAt": "2026-06-20T00:00:00Z"
  }
}
```

중복 open order가 없으면 다음처럼 응답한다.

```json
{
  "duplicateOpenOrderExists": false,
  "duplicateOpenOrderId": null,
  "duplicateOpenOrder": null
}
```

## Duplicate Open Order 기준

다음 상태는 아직 시장/브로커 처리 중이거나 취소 확정 전이므로 open order로 본다.

| Status | Open 여부 |
| --- | --- |
| `CREATED` | yes |
| `SENT` | yes |
| `ACKED` | yes |
| `PARTIALLY_FILLED` | yes |
| `CANCEL_REQUESTED` | yes |
| `FILLED` | no |
| `CANCELED` | no |
| `REJECTED` | no |

같은 주문으로 의심하는 기준은 `portfolioId`, `instrumentId`, `side`, `orderType`, `quantity`, `limitPrice`, `timeInForce`가 같은 open order다. 이 응답은 pre-trade risk의 `duplicateOpenOrderExists`, `duplicateOpenOrderId` context로 전달할 수 있다.

## 3. Order 제출 처리

```http
POST /api/orders/{orderId}/submissions
```

### Request

request body는 없다. 제출 대상은 path variable의 `orderId`로 식별한다.

### Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000033001",
  "intentId": "00000000-0000-0000-0000-000000034001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 0,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "SENT",
  "createdAt": "2026-06-04T00:00:00Z",
  "updatedAt": "2026-06-05T00:00:00Z"
}
```

## 제출 상태 규칙

| Order status | 처리 |
| --- | --- |
| `CREATED` | `SENT`로 전이해 저장 |
| `SENT` | 중복 제출 요청으로 보고 기존 order 반환 |
| 그 외 상태 | `409 Conflict` |

`SENT`는 broker/exchange에 주문 전송 요청이 접수된 내부 상태다. 아직 broker/exchange가 주문을 받아들였다는 의미는 아니며, 실제 접수 확인은 이후 ACK 처리에서 `ACKED`로 표현한다.

존재하지 않는 order를 제출하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order not found"
}
```

제출 가능한 상태가 아닌 order를 제출하면 `409 Conflict`를 반환한다.

```json
{
  "message": "only CREATED orders can be submitted"
}
```

## 4. Broker ACK 반영

```http
POST /api/orders/{orderId}/acknowledgements
Content-Type: application/json
```

### Request

```json
{
  "eventId": "00000000-0000-0000-0000-000000036001"
}
```

`eventId`는 broker/exchange에서 전달받은 이벤트를 식별하는 값이다. 같은 `eventId`가 다시 들어오면 중복 이벤트로 보고 이미 반영된 결과를 반환한다.

### Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000035001",
  "intentId": "00000000-0000-0000-0000-000000037001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 0,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "ACKED",
  "createdAt": "2026-06-04T00:00:00Z",
  "updatedAt": "2026-06-05T00:00:00Z"
}
```

## 5. Broker Reject 반영

```http
POST /api/orders/{orderId}/rejections
Content-Type: application/json
```

### Request

```json
{
  "eventId": "00000000-0000-0000-0000-000000036002"
}
```

### Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000035002",
  "intentId": "00000000-0000-0000-0000-000000037001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 0,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "REJECTED",
  "createdAt": "2026-06-04T00:00:00Z",
  "updatedAt": "2026-06-05T00:00:00Z"
}
```

## ACK/REJECT 상태 규칙

| Order status | ACK 처리 | REJECT 처리 |
| --- | --- | --- |
| `SENT` | `ACKED`로 전이 | `REJECTED`로 전이 |
| `ACKED` | 중복 ACK로 보고 기존 order 반환 | `409 Conflict` |
| `REJECTED` | `409 Conflict` | 중복 REJECT로 보고 기존 order 반환 |
| 그 외 상태 | `409 Conflict` | `409 Conflict` |

ACK와 REJECT는 broker/exchange가 `SENT` 주문에 대해 응답한 결과다. `ACKED`는 주문이 시장에 접수된 상태이고, `REJECTED`는 broker/exchange가 주문을 거절한 상태다.

`eventId`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "eventId is required"
}
```

존재하지 않는 order에 이벤트를 반영하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order not found"
}
```

ACK/REJECT 가능한 상태가 아니거나, 같은 `eventId`가 다른 order 또는 다른 이벤트 타입에 이미 쓰였으면 `409 Conflict`를 반환한다.

```json
{
  "message": "only SENT orders can be acknowledged or rejected"
}
```

## 6. Broker Fill 반영

```http
POST /api/orders/{orderId}/fills
Content-Type: application/json
```

### Request

```json
{
  "fillExecutionId": "00000000-0000-0000-0000-000000039001",
  "fillQuantity": 4,
  "fillPrice": 55000,
  "feeAmount": 40,
  "taxAmount": 12
}
```

### Request Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `fillExecutionId` | required | broker/exchange 체결 이벤트 id |
| `fillQuantity` | required | 이번 이벤트에서 추가 체결된 수량 |
| `fillPrice` | optional | 이번 체결 가격 |
| `feeAmount` | optional | 이번 체결 수수료 |
| `taxAmount` | optional | 이번 체결 세금 |

`fillExecutionId`는 같은 체결 이벤트를 한 번만 반영하기 위한 idempotency key 역할을 한다. 같은 `fillExecutionId`가 다시 들어오면 체결 수량을 다시 더하지 않고 현재 order를 반환한다.

### Partial Fill Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000038001",
  "intentId": "00000000-0000-0000-0000-000000040001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 4,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "PARTIALLY_FILLED",
  "createdAt": "2026-06-04T00:00:00Z",
  "updatedAt": "2026-06-05T00:00:00Z"
}
```

### Full Fill Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000038002",
  "intentId": "00000000-0000-0000-0000-000000040001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "FILLED",
  "createdAt": "2026-06-04T00:00:00Z",
  "updatedAt": "2026-06-05T00:00:00Z"
}
```

## Fill 상태 규칙

| Order status | 처리 |
| --- | --- |
| `ACKED` | 첫 체결 반영 후 `PARTIALLY_FILLED` 또는 `FILLED` |
| `PARTIALLY_FILLED` | 추가 체결 누적 후 `PARTIALLY_FILLED` 또는 `FILLED` |
| `CANCEL_REQUESTED` | cancel-fill race condition으로 보고 남은 체결 반영 허용 |
| 그 외 상태 | `409 Conflict` |

`fillQuantity`를 기존 누적 체결 수량에 더했을 때 원 주문 수량보다 커지면 overfill이므로 `409 Conflict`를 반환한다.

요청 필드가 누락되었거나 숫자 값이 유효하지 않으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "fillQuantity must be greater than zero"
}
```

존재하지 않는 order에 fill을 반영하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order not found"
}
```

체결 가능한 상태가 아니거나, 같은 `fillExecutionId`가 다른 order에 이미 쓰였으면 `409 Conflict`를 반환한다.

```json
{
  "message": "only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled"
}
```

## 7. Order 취소 요청

```http
POST /api/orders/{orderId}/cancel-requests
```

### Request

request body는 없다. 취소 요청 대상은 path variable의 `orderId`로 식별한다.

### Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000041001",
  "intentId": "00000000-0000-0000-0000-000000043001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 0,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "CANCEL_REQUESTED",
  "createdAt": "2026-06-05T00:00:00Z",
  "updatedAt": "2026-06-06T00:00:00Z"
}
```

## 8. Broker Cancel Confirmation 반영

```http
POST /api/orders/{orderId}/cancel-confirmations
Content-Type: application/json
```

### Request

```json
{
  "eventId": "00000000-0000-0000-0000-000000042002"
}
```

### Response

```json
{
  "orderId": "00000000-0000-0000-0000-000000041002",
  "intentId": "00000000-0000-0000-0000-000000043001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "filledQuantity": 4,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "status": "CANCELED",
  "createdAt": "2026-06-05T00:00:00Z",
  "updatedAt": "2026-06-06T00:00:00Z"
}
```

## Cancel 상태 규칙

| Order status | Cancel request | Cancel confirmation |
| --- | --- | --- |
| `ACKED` | `CANCEL_REQUESTED`로 전이 | `409 Conflict` |
| `PARTIALLY_FILLED` | `CANCEL_REQUESTED`로 전이 | `409 Conflict` |
| `CANCEL_REQUESTED` | 중복 요청으로 보고 기존 order 반환 | `CANCELED`로 전이 |
| `CANCELED` | `409 Conflict` | 중복 confirmation으로 보고 기존 order 반환 |
| 그 외 상태 | `409 Conflict` | `409 Conflict` |

취소 요청은 아직 broker/exchange가 취소를 완료했다는 뜻이 아니다. 내부적으로 "취소 요청을 보냈다"는 상태를 `CANCEL_REQUESTED`로 기록하고, broker/exchange의 취소 완료 이벤트가 들어오면 `CANCELED`로 전이한다.

cancel confirmation의 `eventId`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "eventId is required"
}
```

존재하지 않는 order를 취소하려고 하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order not found"
}
```

취소 가능한 상태가 아니거나, 같은 `eventId`가 다른 order 또는 다른 이벤트 타입에 이미 쓰였으면 `409 Conflict`를 반환한다.

```json
{
  "message": "only ACKED or PARTIALLY_FILLED orders can be canceled"
}
```
