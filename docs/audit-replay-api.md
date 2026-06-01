# Audit Replay API Guide

`audit-replay` API는 주문 상태 불일치나 장애 상황에서 이벤트 기반으로 주문을 다시 확인하기 위한 운영 진단 API다.

현재 API는 크게 네 가지 흐름을 제공한다.

```text
1. 전체 replay consistency report 조회
2. 특정 order consistency 조회
3. 특정 order execution replay 조회
4. 특정 order audit trail 조회
```

## 진단 흐름

운영자가 불일치 주문을 조사할 때 권장 흐름은 다음과 같다.

```text
전체 consistency report
    -> 불일치 orderId 확인
    -> 단건 consistency check
    -> 단건 execution replay
    -> order audit trail 확인
```

각 API의 역할은 다르다.

| API | 목적 |
| --- | --- |
| Consistency Report | 전체 주문 중 이벤트 기반 replay 결과와 현재 order row가 다른 주문을 찾는다. |
| Single Consistency | 특정 주문의 실제 상태/수량과 replay 상태/수량 차이를 확인한다. |
| Execution Replay | 특정 주문의 audit event를 적용했을 때 어떤 상태가 되어야 하는지 재현한다. |
| Audit Trail | 특정 주문에 쌓인 execution/fill 이벤트 timeline을 확인한다. |

## 1. 전체 Consistency Report 조회

### 전체 주문 조회

```http
GET /api/audit-replay/order-replay/consistency-report
```

### 불일치 주문만 조회

```http
GET /api/audit-replay/order-replay/consistency-report?inconsistentOnly=true
```

`inconsistentOnly=true`를 사용하면 전체 주문을 모두 검사하되, `results` 목록에는 불일치 주문만 포함한다.

집계 필드는 항상 전체 검사 기준이다.

```json
{
  "totalCount": 3,
  "consistentCount": 1,
  "inconsistentCount": 2,
  "inconsistentRatio": 0.6667,
  "results": [
    {
      "orderId": "00000000-0000-0000-0000-000000017004",
      "consistent": false,
      "mismatchReasons": ["FILLED_QUANTITY_MISMATCH"],
      "actualStatus": "PARTIALLY_FILLED",
      "replayedStatus": "PARTIALLY_FILLED",
      "actualFilledQuantity": 3,
      "replayedFilledQuantity": 4,
      "appliedEventCount": 2,
      "checkedAt": "2026-05-31T01:00:00Z"
    }
  ],
  "checkedAt": "2026-05-31T01:00:00Z"
}
```

### 필드 의미

| Field | Meaning |
| --- | --- |
| `totalCount` | 검사한 전체 주문 수 |
| `consistentCount` | 현재 order row와 replay 결과가 일치한 주문 수 |
| `inconsistentCount` | 현재 order row와 replay 결과가 불일치한 주문 수 |
| `inconsistentRatio` | `inconsistentCount / totalCount` 비율. 주문이 없으면 `0.0000` |
| `results` | 개별 consistency 결과 목록 |
| `checkedAt` | report 생성 시각 |

## 2. 단건 Consistency 조회

```http
GET /api/audit-replay/order-replay/consistency/{orderId}
```

특정 주문의 현재 저장 상태와 replay 결과를 비교한다.

```json
{
  "orderId": "00000000-0000-0000-0000-000000021001",
  "consistent": false,
  "mismatchReasons": ["STATUS_MISMATCH"],
  "actualStatus": "PARTIALLY_FILLED",
  "replayedStatus": "FILLED",
  "actualFilledQuantity": 10,
  "replayedFilledQuantity": 10,
  "appliedEventCount": 3,
  "checkedAt": "2026-05-31T04:10:00Z"
}
```

### mismatchReasons

| Reason | Meaning |
| --- | --- |
| `STATUS_MISMATCH` | 현재 order status와 replay status가 다르다. 상태 전이 저장 누락이나 잘못된 상태 갱신을 의심할 수 있다. |
| `FILLED_QUANTITY_MISMATCH` | 현재 filled quantity와 replay 누적 체결 수량이 다르다. fill 누락/중복 처리나 수량 누적 로직을 확인해야 한다. |

`mismatchReasons`가 빈 목록이면 `consistent`는 `true`다.

## 3. 단건 Execution Replay 조회

### 저장된 order 수량으로 replay

```http
GET /api/audit-replay/order-replay/stored-orders/{orderId}
```

저장된 order row를 조회해 `Order.quantity`를 원 주문 수량으로 사용한다.

운영자가 원 주문 수량을 따로 찾지 않아도 되므로 일반적인 단건 조사에서는 이 API를 먼저 사용하는 것이 좋다.

```json
{
  "orderId": "00000000-0000-0000-0000-000000019003",
  "initialStatus": "SENT",
  "replayedStatus": "PARTIALLY_FILLED",
  "orderQuantity": 12,
  "replayedFilledQuantity": 5,
  "appliedEventCount": 2,
  "replayedAt": "2026-06-01T00:10:00Z"
}
```

### 명시한 orderQuantity로 replay

```http
GET /api/audit-replay/order-replay/{orderId}?orderQuantity=10
```

특정 주문의 audit trail 이벤트를 순서대로 적용해, 이벤트 기준으로 어떤 상태가 되어야 하는지 재현한다.

이 API는 원 주문 수량을 `orderQuantity` query parameter로 직접 받는다.

저장된 order row와 다른 수량을 넣어 replay 방어 로직을 확인하거나, 아직 order 저장소 조회를 사용하지 않는 테스트성 호출에 사용할 수 있다.

```json
{
  "orderId": "00000000-0000-0000-0000-000000019001",
  "initialStatus": "SENT",
  "replayedStatus": "FILLED",
  "orderQuantity": 10,
  "replayedFilledQuantity": 10,
  "appliedEventCount": 3,
  "replayedAt": "2026-05-31T03:00:00Z"
}
```

### replay 규칙

현재 replay는 `SENT` 이후 execution 이벤트와 fill 이벤트를 재현한다.

| Event | Replay result |
| --- | --- |
| `ACKNOWLEDGED` | `ACKED` |
| `REJECTED` | `REJECTED` |
| `CANCEL_CONFIRMED` | `CANCELED` |
| `FILL` | 체결 수량을 누적해 `PARTIALLY_FILLED` 또는 `FILLED` |

누적 체결 수량이 원 주문 수량을 초과하면 replay 오류로 처리한다.

## 4. Order Audit Trail 조회

```http
GET /api/audit-replay/order-audit-trails/{orderId}
```

특정 주문의 execution event와 fill event를 시간순 timeline으로 조회한다.

```json
{
  "orderId": "00000000-0000-0000-0000-000000020001",
  "events": [
    {
      "eventId": "00000000-0000-0000-0000-000000020101",
      "orderId": "00000000-0000-0000-0000-000000020001",
      "source": "ORDER_EXECUTION",
      "eventType": "ACKNOWLEDGED",
      "fillQuantity": null,
      "fillPrice": null,
      "feeAmount": null,
      "taxAmount": null,
      "occurredAt": "2026-05-31T04:00:00Z"
    },
    {
      "eventId": "00000000-0000-0000-0000-000000020201",
      "orderId": "00000000-0000-0000-0000-000000020001",
      "source": "FILL_EXECUTION",
      "eventType": "FILL",
      "fillQuantity": 5,
      "fillPrice": 55000,
      "feeAmount": 15,
      "taxAmount": 5,
      "occurredAt": "2026-05-31T04:01:00Z"
    }
  ]
}
```

### source

| Source | Meaning |
| --- | --- |
| `ORDER_EXECUTION` | ACK, REJECT, CANCEL confirmation 같은 주문 실행 상태 이벤트 |
| `FILL_EXECUTION` | 체결 수량/가격/수수료/세금 정보를 가진 fill 이벤트 |

Audit trail은 replay의 입력 데이터다. 주문 상태 불일치가 발생했을 때 어떤 이벤트가 어떤 순서로 적용됐는지 확인하는 데 사용한다.

## Error Response

`audit-replay` API 오류 응답은 다음 형태를 사용한다.

```json
{
  "message": "orderQuantity is required"
}
```

대표 오류는 다음과 같다.

| Scenario | HTTP Status | Message |
| --- | --- | --- |
| 필수 query parameter 누락 | `400 Bad Request` | `<parameter> is required` |
| UUID/path/query parameter 변환 실패 | `400 Bad Request` | `invalid request argument` |
| replay/consistency 도메인 오류 | `400 Bad Request` | 도메인 예외 메시지 |

예외 처리는 `AuditReplayExceptionHandler`가 담당한다.

```java
@RestControllerAdvice(basePackages = "com.multiassetoms.auditreplay")
public class AuditReplayExceptionHandler {
}
```

`basePackages`로 적용 범위를 제한해 다른 모듈의 controller 예외 처리와 충돌하지 않도록 했다.

## Curl Examples

전체 report:

```bash
curl "http://localhost:8080/api/audit-replay/order-replay/consistency-report"
```

불일치 report:

```bash
curl "http://localhost:8080/api/audit-replay/order-replay/consistency-report?inconsistentOnly=true"
```

단건 consistency:

```bash
curl "http://localhost:8080/api/audit-replay/order-replay/consistency/00000000-0000-0000-0000-000000021001"
```

단건 replay:

```bash
curl "http://localhost:8080/api/audit-replay/order-replay/00000000-0000-0000-0000-000000019001?orderQuantity=10"
```

저장된 order 수량 기반 replay:

```bash
curl "http://localhost:8080/api/audit-replay/order-replay/stored-orders/00000000-0000-0000-0000-000000019003"
```

audit trail:

```bash
curl "http://localhost:8080/api/audit-replay/order-audit-trails/00000000-0000-0000-0000-000000020001"
```

## Notes

- 현재 API는 MVP 내부 운영용 진단 API다.
- 인증/권한, pagination, 조건 조회는 아직 포함하지 않았다.
- 저장된 order 수량 기반 replay API는 클라이언트가 원 주문 수량을 직접 넘기지 않아도 된다.
- 명시적 `orderQuantity` replay API는 테스트성 호출이나 수량 검증 실험을 위해 유지한다.
- 내부 구현에서는 저장소 조회/API 입력 조립용 query service와 replay/consistency 계산 service를 분리한다.
  - query service는 저장된 order row를 찾아 계산 입력을 준비한다.
  - 계산 service는 이미 준비된 입력을 기준으로 replay 또는 mismatch 판단 규칙을 수행한다.
