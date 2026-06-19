# Market Data API Guide

`market-data` API는 instrument별 최신 시장 가격을 저장하고 조회하는 진입점이다.

현재 MVP에서는 pre-trade risk의 price band 판단, post-trade unrealized PnL 계산, 운영 화면의 현재가 확인을 위한 최소 가격 소스를 제공한다.

## 현재 MVP 흐름

```text
Market price upsert
    -> latest market price 저장
    -> price query
    -> risk / PnL / 운영 조회에서 참조
```

## 1. Latest Market Price 저장

```http
PUT /api/market-data/instruments/{instrumentId}/prices/latest
Content-Type: application/json
```

### Request

```json
{
  "price": 55000,
  "observedAt": "2026-06-11T09:00:00Z"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `price` | yes | instrument의 최신 시장 가격. 0보다 커야 한다. |
| `observedAt` | no | 가격이 관측된 시각. 없으면 서버 저장 시각을 사용한다. |

### Response

```json
{
  "instrumentId": "005930",
  "price": 55000,
  "observedAt": "2026-06-11T09:00:00Z",
  "updatedAt": "2026-06-11T09:00:01Z"
}
```

같은 `instrumentId`로 다시 저장하면 기존 latest price를 교체한다.

## 2. Latest Market Price 조회

```http
GET /api/market-data/instruments/{instrumentId}/prices/latest
```

### Response

```json
{
  "instrumentId": "005930",
  "price": 55000,
  "observedAt": "2026-06-11T09:00:00Z",
  "updatedAt": "2026-06-11T09:00:01Z"
}
```

## Error Response

`price`가 없으면 `400 Bad Request`를 반환한다.

```json
{
  "message": "price is required"
}
```

`price`가 0 이하이면 `400 Bad Request`를 반환한다.

```json
{
  "message": "price must be greater than zero"
}
```

저장된 latest price가 없으면 `404 Not Found`를 반환한다.

```json
{
  "message": "market price not found"
}
```

## 운영 메모

- latest price는 instrument별 현재 기준 가격을 빠르게 조회하기 위한 값이다.
- `PUT`을 사용한 이유는 `/{instrumentId}/prices/latest`라는 단일 latest price 리소스를 교체하는 동작이기 때문이다.
- 현재는 in-memory repository에 최신값만 보관한다.
- 이후 market replay, 시계열 차트, 과거 가격 검증이 필요해지면 `MarketPriceTick` 또는 `MarketPriceHistory` 테이블을 별도로 추가한다.
- pre-trade risk의 latest price band evaluation API는 이 latest price로 `lowerPriceBand`, `upperPriceBand`를 계산한다.
- post-trade의 latest market price 기반 unrealized PnL API는 이 latest price를 `marketPrice`로 사용한다.
