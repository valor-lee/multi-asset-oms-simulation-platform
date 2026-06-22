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

Trade(CAPTURED)
    -> settlement schedule
    -> Settlement(PENDING), Trade(SETTLEMENT_PENDING)
    -> settlement confirmation
    -> Settlement(SETTLED), Trade(SETTLED)
    -> ledger posting
    -> PositionLedgerEntry, CashLedgerEntry
    -> balance query
    -> current position / current cash
    -> PnL query
    -> realized PnL / unrealized PnL snapshot
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
- 다음 단계에서는 `Trade(SETTLED)`를 position/cash ledger에 반영하는 API를 열 수 있다.

## 2. Trade Settlement 예정 등록

```http
POST /api/post-trade/trades/{tradeId}/settlements
Content-Type: application/json
```

### Request

```json
{
  "settlementDate": "2026-06-09"
}
```

### Response

```json
{
  "settlementId": "00000000-0000-0000-0000-000000048001",
  "tradeId": "00000000-0000-0000-0000-000000047001",
  "settlementDate": "2026-06-09",
  "status": "PENDING",
  "createdAt": "2026-06-07T00:00:00Z",
  "settledAt": null,
  "updatedAt": "2026-06-07T00:00:00Z"
}
```

## 3. Settlement 완료 확인

```http
POST /api/post-trade/settlements/{settlementId}/confirmations
```

### Request

request body는 없다. 완료 처리할 settlement는 path variable의 `settlementId`로 식별한다.

### Response

```json
{
  "settlementId": "00000000-0000-0000-0000-000000048002",
  "tradeId": "00000000-0000-0000-0000-000000047002",
  "settlementDate": "2026-06-09",
  "status": "SETTLED",
  "createdAt": "2026-06-07T00:00:00Z",
  "settledAt": "2026-06-10T00:00:00Z",
  "updatedAt": "2026-06-10T00:00:00Z"
}
```

## Settlement 상태 규칙

| 대상 상태 | 처리 |
| --- | --- |
| `Trade(CAPTURED)` | settlement 생성, trade를 `SETTLEMENT_PENDING`으로 전이 |
| 이미 settlement가 있는 trade | 기존 settlement 반환 |
| `Settlement(PENDING)` | settlement와 trade를 `SETTLED`로 전이 |
| `Settlement(SETTLED)` | 중복 완료 요청으로 보고 기존 settlement 반환 |
| 그 외 상태 | `409 Conflict` |

`settlementDate`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "settlementDate is required"
}
```

존재하지 않는 trade 또는 settlement를 요청하면 `404 Not Found`를 반환한다.

```json
{
  "message": "trade not found"
}
```

상태가 맞지 않으면 `409 Conflict`를 반환한다.

```json
{
  "message": "only CAPTURED trades can be scheduled for settlement"
}
```

## 4. Post-Settlement Ledger Posting

```http
POST /api/post-trade/trades/{tradeId}/ledger-postings
```

### Request

request body는 없다. ledger에 반영할 trade는 path variable의 `tradeId`로 식별한다.

### Response

```json
{
  "positionLedgerEntry": {
    "entryId": "00000000-0000-0000-0000-000000050001",
    "tradeId": "00000000-0000-0000-0000-000000049001",
    "portfolioId": "portfolio-1",
    "instrumentId": "005930",
    "side": "BUY",
    "quantityDelta": 10,
    "postedAt": "2026-06-07T00:00:00Z"
  },
  "cashLedgerEntry": {
    "entryId": "00000000-0000-0000-0000-000000051001",
    "tradeId": "00000000-0000-0000-0000-000000049001",
    "portfolioId": "portfolio-1",
    "side": "BUY",
    "cashDelta": -550100,
    "postedAt": "2026-06-07T00:00:00Z"
  }
}
```

## Ledger Posting 규칙

| Trade status | 처리 |
| --- | --- |
| `SETTLED` | position ledger와 cash ledger에 함께 posting |
| 이미 posting된 trade | 기존 position/cash ledger entry 반환 |
| 그 외 상태 | `409 Conflict` |

BUY trade는 position을 증가시키고 cash를 감소시킨다. SELL trade는 position을 감소시키고 cash를 증가시킨다. cash delta에는 `grossNotional`, `feeAmount`, `taxAmount`가 반영된다.

존재하지 않는 trade를 요청하면 `404 Not Found`를 반환한다.

```json
{
  "message": "trade not found"
}
```

ledger posting 대상이 아니거나 cash 계산에 필요한 총 체결금액이 없으면 `409 Conflict`를 반환한다.

```json
{
  "message": "only SETTLED trades can be posted to ledgers"
}
```

## 5. Current Position 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}
```

### Response

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10
}
```

position ledger에 아직 반영된 내역이 없으면 `quantity`는 `0`으로 응답한다.

## 6. Current Cash 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/cash
```

### Response

```json
{
  "portfolioId": "portfolio-1",
  "cash": -550100
}
```

cash ledger에 아직 반영된 내역이 없으면 `cash`는 `0`으로 응답한다.

## Balance Query 메모

- position/cash 조회 API는 ledger entry를 새로 만들지 않고 현재 누적값만 반환한다.
- BUY trade가 ledger에 posting되면 position은 증가하고 cash는 감소한다.
- SELL trade가 ledger에 posting되면 position은 감소하고 cash는 증가한다.
- 현재 API는 in-memory repository의 누적값을 그대로 조회한다. DB 전환 시에는 portfolio/instrument 기준 인덱스와 pagination 없는 단건 조회 계약을 유지하면 된다.

## 7. Average Cost Posting

```http
POST /api/post-trade/trades/{tradeId}/average-cost-postings
```

### Request

request body는 없다. 평균단가 원장에 반영할 trade는 path variable의 `tradeId`로 식별한다.

### Response

```json
{
  "entryId": "00000000-0000-0000-0000-000000079001",
  "tradeId": "00000000-0000-0000-0000-000000078001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "quantity": 10,
  "costDelta": 550100,
  "positionQuantity": 10,
  "costBasis": 550100,
  "averageCost": 55010,
  "postedAt": "2026-06-21T00:00:00Z"
}
```

## Average Cost Posting 규칙

| Trade | 처리 |
| --- | --- |
| `SETTLED` BUY | `grossNotional + feeAmount + taxAmount`를 cost basis에 더한다. |
| `SETTLED` SELL | 현재 평균단가 기준으로 매도 수량만큼 cost basis를 줄인다. |
| 이미 posting된 trade | 기존 average cost entry 반환 |
| 그 외 상태 | `409 Conflict` |

MVP에서는 long position만 지원한다. 현재 보유 수량보다 큰 SELL trade를 평균단가 원장에 반영하려 하면 `409 Conflict`를 반환한다.

```json
{
  "message": "sell quantity exceeds current position"
}
```

## 8. Current Average Cost 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/average-cost
```

### Response

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10,
  "costBasis": 550100,
  "averageCost": 55010,
  "updatedAt": "2026-06-21T00:00:00Z"
}
```

평균단가 원장에 아직 반영된 내역이 없으면 `quantity`, `costBasis`, `averageCost`는 모두 `0`으로 응답한다.

## 9. Realized PnL Posting

```http
POST /api/post-trade/trades/{tradeId}/realized-pnl-postings
Content-Type: application/json
```

### Request

```json
{
  "averageCost": 54000
}
```

`averageCost`는 매도 수량에 대응하는 평균 원가다. realized PnL은 실제 매도가 확정된 뒤 계산되는 손익이므로 `SETTLED` 상태의 `SELL` trade만 posting 대상이다.

### Response

```json
{
  "entryId": "00000000-0000-0000-0000-000000053001",
  "tradeId": "00000000-0000-0000-0000-000000052001",
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10,
  "averageSellPrice": 55000.0000000000,
  "averageCost": 54000,
  "grossNotional": 550000,
  "feeAmount": 100,
  "taxAmount": 30,
  "realizedPnl": 9870,
  "postedAt": "2026-06-07T00:00:00Z"
}
```

계산식은 다음과 같다.

```text
realizedPnl = grossNotional - quantity * averageCost - feeAmount - taxAmount
```

이미 같은 trade에 realized PnL entry가 있으면 중복 posting으로 보고 기존 entry를 반환한다.

`averageCost`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "averageCost is required"
}
```

존재하지 않는 trade를 요청하면 `404 Not Found`를 반환한다.

```json
{
  "message": "trade not found"
}
```

`SETTLED`가 아니거나 `SELL`이 아닌 trade는 `409 Conflict`를 반환한다.

```json
{
  "message": "only SELL trades can produce realized PnL"
}
```

## 10. Current Average Cost 기준 Realized PnL Posting

```http
POST /api/post-trade/trades/{tradeId}/realized-pnl-postings/current-average-cost
```

request body는 없다. trade의 portfolio/instrument 기준 current average cost를 조회해 realized PnL을 계산한다.

이미 해당 SELL trade가 average cost 원장에 posting됐다면, current average cost가 0으로 바뀌는 전량 매도 케이스를 피하기 위해 그 trade의 `AverageCostEntry.costDelta`에서 매도 직전 평균단가를 복원한다. 아직 average cost posting 전이면 현재 average cost를 사용한다.

응답 구조와 오류 응답은 기본 realized PnL posting API와 동일하다.

## 11. Current Realized PnL 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/realized-pnl
```

### Response

```json
{
  "portfolioId": "portfolio-1",
  "realizedPnl": 9870
}
```

realized PnL entry가 아직 없으면 `realizedPnl`은 `0`으로 응답한다.

## 12. Unrealized PnL Snapshot 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl?averageCost=54000&marketPrice=55000
```

### Query Parameters

| Name | 의미 |
| --- | --- |
| `averageCost` | 현재 보유 position의 평균 원가 |
| `marketPrice` | 현재 시장 가격 |

### Response

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10,
  "averageCost": 54000,
  "marketPrice": 55000,
  "costBasis": 540000,
  "marketValue": 550000,
  "unrealizedPnl": 10000,
  "valuedAt": "2026-06-07T00:00:00Z"
}
```

unrealized PnL은 아직 매도하지 않은 보유분의 평가손익이다. 시장 가격이 바뀔 때마다 값이 달라지므로 ledger entry로 저장하지 않고 조회 시점의 snapshot으로 계산한다.

계산식은 다음과 같다.

```text
costBasis = quantity * averageCost
marketValue = quantity * marketPrice
unrealizedPnl = marketValue - costBasis
```

현재 position이 없으면 `quantity`, `costBasis`, `marketValue`, `unrealizedPnl`은 모두 `0`으로 응답한다.

## 13. Latest Market Price 기준 Unrealized PnL Snapshot 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest?averageCost=54000
```

### Query Parameters

| Name | 의미 |
| --- | --- |
| `averageCost` | 현재 보유 position의 평균 원가 |

### Response

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10,
  "averageCost": 54000,
  "marketPrice": 55000,
  "costBasis": 540000,
  "marketValue": 550000,
  "unrealizedPnl": 10000,
  "valuedAt": "2026-06-07T00:00:00Z"
}
```

이 API는 `market-data`에 저장된 instrument별 latest market price를 조회해 `marketPrice`로 사용한다. 호출자가 직접 가격을 넘기는 기본 snapshot API보다 운영 흐름에 더 가깝다.

저장된 latest market price가 없으면 `404 Not Found`를 반환한다.

```json
{
  "message": "market price not found"
}
```

## 14. Current Average Cost + Latest Market Price 기준 Unrealized PnL Snapshot 조회

```http
GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest/current-average-cost
```

query parameter는 없다. average cost는 post-trade average cost 원장에서 조회하고, market price는 market-data latest price에서 조회한다.

### Response

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "quantity": 10,
  "averageCost": 54000,
  "marketPrice": 55000,
  "costBasis": 540000,
  "marketValue": 550000,
  "unrealizedPnl": 10000,
  "valuedAt": "2026-06-07T00:00:00Z"
}
```

이 API는 운영 흐름에서 가장 자동화된 unrealized PnL 조회 경계다.

```text
current position + current average cost + latest market price
    -> unrealized PnL snapshot
```

## PnL 메모

- realized PnL은 매도가 확정된 뒤 실제로 실현된 손익이다.
- unrealized PnL은 아직 보유 중인 position을 현재가로 평가한 손익이다.
- realized PnL posting은 상태를 변경하고 entry를 만들기 때문에 `POST`로 둔다.
- unrealized PnL snapshot은 저장하지 않는 계산 결과이므로 `GET`으로 둔다.
- latest market price 기반 snapshot은 market-data의 현재 가격을 사용하므로, 가격 수집/갱신이 먼저 되어 있어야 한다.
- `averageCost`를 요청값으로 받는 API는 테스트/시뮬레이션 경계로 유지한다.
- current average cost 기반 API는 post-trade 평균단가 원장을 내부 조회하므로 운영 흐름에 더 가깝다.
