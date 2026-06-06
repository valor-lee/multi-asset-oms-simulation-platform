# Post-Trade API Guide

`post-trade` API는 execution에서 체결이 끝난 order를 거래, 정산, 원장 반영 흐름으로 넘기는 진입점이다.

현재 MVP 흐름에서는 다음 순서로 사용한다.

```text
Order(FILLED)
    -> trade capture
    -> Trade(CAPTURED)

Order(CANCELED with filledQuantity > 0)
    -> trade capture
    -> Trade(CAPTURED)
```

## 1. Execution Order를 Trade로 Capture

```http
POST /api/post-trade/orders/{orderId}/trades
```

### Request

request body는 없다. trade로 캡처할 execution order는 path variable의 `orderId`로 식별한다.

### Response

```json
{
  "tradeId": "00000000-0000-0000-0000-000000045001",
  "orderId": "00000000-0000-0000-0000-000000044001",
  "intentId": "00000000-0000-0000-0000-000000046001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "quantity": 10,
  "averageFillPrice": 55000.0000000000,
  "grossNotional": 550000,
  "feeAmount": 100,
  "taxAmount": 30,
  "status": "CAPTURED",
  "capturedAt": "2026-06-07T00:00:00Z",
  "settledAt": null,
  "updatedAt": "2026-06-07T00:00:00Z"
}
```

## Capture 대상

| Order status | 처리 |
| --- | --- |
| `FILLED` | 전체 체결 수량을 trade로 캡처 |
| `CANCELED` with filled quantity | 부분 체결 후 취소된 수량만 trade로 캡처 |
| 이미 캡처된 order | 기존 trade 반환 |
| 그 외 상태 | `409 Conflict` |

execution의 `Order`는 주문 상태 머신을 관리하는 객체이고, post-trade의 `Trade`는 실제 체결된 거래 결과를 후처리하기 위한 객체다.

부분 체결 후 취소된 order도 체결된 수량이 있다면 settlement, position, cash, PnL 대상이므로 trade로 캡처한다.

## Error Response

존재하지 않는 order를 capture하려고 하면 `404 Not Found`를 반환한다.

```json
{
  "message": "order not found"
}
```

trade로 캡처할 수 없는 상태이면 `409 Conflict`를 반환한다.

```json
{
  "message": "only FILLED or partially filled CANCELED orders can be captured"
}
```

## 운영 메모

- `fillExecutionId` 단위의 체결 상세는 execution의 fill repository에 남아 있다.
- trade capture는 fill execution들을 요약해 평균 체결가, 총 체결금액, 수수료, 세금을 trade에 반영한다.
- 일부 fill에 가격이 없으면 평균 체결가와 총 체결금액은 비워둔다.
- 일부 priced fill에 수수료 또는 세금이 없으면 해당 합계는 비워둔다.
- 다음 단계에서는 `Trade(CAPTURED)`를 settlement 예정 상태로 넘기는 API를 열 수 있다.
