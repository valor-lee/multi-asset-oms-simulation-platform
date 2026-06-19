# Pre-Trade Risk API Guide

`pre-trade-risk` API는 생성된 `OrderIntent`를 실제 OMS order로 전환하기 전에 사전 리스크 규칙으로 평가하는 진입점이다.

평가 대상 intent는 `CREATED` 상태여야 한다. 평가 결과에 따라 intent 상태는 `RISK_APPROVED` 또는 `RISK_REJECTED`로 저장된다.

## 1. Order Intent Risk 평가

```http
POST /api/pre-trade-risk/order-intents/{intentId}/evaluations
Content-Type: application/json
```

### Request

request body는 선택값이다. body를 생략하면 기본 규칙과 빈 context로 평가한다.

```json
{
  "maxOrderQty": 10,
  "maxOrderNotional": 550000,
  "maxPositionQty": 100,
  "currentPositionQty": 90,
  "duplicateOpenOrderExists": false,
  "duplicateOpenOrderId": null,
  "lowerPriceBand": 50000,
  "upperPriceBand": 60000,
  "killSwitchEnabled": false
}
```

### Response

```json
{
  "intent": {
    "intentId": "00000000-0000-0000-0000-000000029001",
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
    "status": "RISK_APPROVED",
    "idempotencyKey": "manual-key-1",
    "createdBy": "operator",
    "createdAt": "2026-06-05T00:00:00Z",
    "updatedAt": "2026-06-05T00:00:00Z"
  },
  "riskCheckResult": {
    "intentId": "00000000-0000-0000-0000-000000029001",
    "decision": "APPROVED",
    "reason": "approved",
    "checkedAt": "2026-06-05T00:00:00Z",
    "ruleResults": []
  }
}
```

## Request Fields

| Field | Meaning |
| --- | --- |
| `maxOrderQty` | 주문 수량 한도 |
| `maxOrderNotional` | 주문 금액 한도 |
| `maxPositionQty` | 평가 후 포지션 수량 한도 |
| `currentPositionQty` | 현재 보유 수량 |
| `duplicateOpenOrderExists` | 같은 주문으로 의심되는 open order 존재 여부 |
| `duplicateOpenOrderId` | 매칭된 open order id |
| `lowerPriceBand` | 허용 최저 주문 가격 |
| `upperPriceBand` | 허용 최고 주문 가격 |
| `killSwitchEnabled` | kill switch 활성화 여부 |

## Error Response

존재하지 않는 intent를 평가하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order intent not found"
}
```

이미 risk 평가가 끝난 intent처럼 `CREATED` 상태가 아닌 intent를 다시 평가하면 `409 Conflict`를 반환한다.

```json
{
  "message": "only CREATED order intents can be evaluated by pre-trade risk"
}
```

## 운영 메모

- `idempotencyKey`는 주문 의도 생성 요청의 재시도 방어다.
- 같은 payload지만 다른 `idempotencyKey`로 생성된 주문 의심 케이스는 `duplicateOpenOrderExists`와 `duplicateOpenOrderId`로 risk context에 전달한다.
- duplicate open order context는 execution의 `GET /api/orders/duplicate-open-order` 조회 결과로 채울 수 있다.
- 기본 평가 API에서는 price band도 호출자가 `lowerPriceBand`, `upperPriceBand`를 직접 전달한다.

## 2. Latest Market Price 기준 Risk 평가

```http
POST /api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band
Content-Type: application/json
```

이 API는 `market-data`에 저장된 instrument별 latest market price를 조회해 price band context를 구성한 뒤 risk 평가를 수행한다.

### Request

```json
{
  "maxOrderQty": 10,
  "maxOrderNotional": 550000,
  "maxPositionQty": 100,
  "currentPositionQty": 90,
  "duplicateOpenOrderExists": false,
  "duplicateOpenOrderId": null,
  "killSwitchEnabled": false,
  "priceBandRate": 0.1
}
```

`priceBandRate`는 latest market price 기준 허용 가격 비율이다. `0.1`이면 -10% ~ +10% 밴드를 의미한다.

### Price Band 계산

```text
lowerPriceBand = latestPrice * (1 - priceBandRate)
upperPriceBand = latestPrice * (1 + priceBandRate)
```

예를 들어 latest price가 `55000`, `priceBandRate`가 `0.1`이면 허용 가격 구간은 다음과 같다.

```text
49500 <= limitPrice <= 60500
```

응답 구조는 기본 risk evaluation API와 동일하다.

### Error Response

`priceBandRate`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "priceBandRate is required"
}
```

`priceBandRate`가 0보다 작거나 1보다 크면 `400 Bad Request`를 반환한다.

```json
{
  "message": "priceBandRate must be between 0 and 1"
}
```

market-data에 latest market price가 없으면 `404 Not Found`를 반환한다.

```json
{
  "message": "market price not found"
}
```
