# Execution API Guide

`execution` API는 사전 리스크를 통과한 `OrderIntent`를 실제 OMS가 관리할 `Order`로 전환하는 진입점이다.

현재 MVP 흐름에서는 다음 순서로 사용한다.

```text
OrderIntent(CREATED)
    -> pre-trade risk 평가
    -> OrderIntent(RISK_APPROVED)
    -> order 변환
    -> Order(CREATED), OrderIntent(CONVERTED_TO_ORDER)
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
