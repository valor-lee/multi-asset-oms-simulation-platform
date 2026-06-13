# RESTful API Strategy

이 문서는 multi-asset OMS에서 HTTP API를 추가할 때 반복해서 참고하기 위한 설계 기준이다.

목표는 "REST를 교과서적으로 완벽히 구현"하는 것이 아니라, 금융 주문/체결/정산 도메인에서 운영자가 이해하기 쉽고, 테스트하기 쉽고, 장애 상황에서 추적 가능한 API 계약을 꾸준히 유지하는 것이다.

## 기본 원칙

### 리소스 중심으로 URL을 만든다

URL은 동작 이름보다 다루는 대상을 먼저 드러낸다.

좋은 예:

```http
POST /api/order-intents/manual
GET /api/audit-replay/order-replay/consistency/{orderId}
GET /api/audit-replay/order-audit-trails/{orderId}
```

피하고 싶은 예:

```http
POST /api/createManualOrderIntent
GET /api/checkOrderConsistency
GET /api/replayOrderExecution
```

동작 이름이 URL에 많아지면 controller가 늘어날수록 API가 RPC처럼 흩어진다. 반대로 리소스 이름을 기준으로 잡으면 "무엇을 생성/조회/변경하는 API인가"가 먼저 보인다.

### HTTP method에 의미를 둔다

같은 URL이라도 method가 다르면 의미가 달라진다.

| Method | 사용 기준 |
| --- | --- |
| `GET` | 상태를 바꾸지 않고 조회한다. |
| `POST` | 새 리소스를 생성하거나, 서버가 계산/처리를 수행한다. |
| `PUT` | 리소스 전체를 교체한다. 현재 프로젝트에서는 신중히 사용한다. |
| `PATCH` | 리소스 일부 상태를 변경한다. 상태 전이 API에서 후보가 될 수 있다. |
| `DELETE` | 리소스를 삭제한다. 금융 도메인에서는 실제 삭제보다 취소/무효 상태 전이가 더 자주 맞다. |

OMS에서는 audit trail, order event, trade ledger 같은 데이터가 감사 대상이므로 물리 삭제 API는 기본적으로 만들지 않는다. 취소가 필요하면 `DELETE`보다 `POST /orders/{orderId}/cancel-requests`처럼 "취소 요청"이라는 새 리소스를 만드는 편이 추적에 좋다.

## URL Naming

### 복수형 명사를 기본으로 쓴다

리소스 컬렉션은 복수형을 사용한다.

```http
GET /api/orders
GET /api/orders/{orderId}
GET /api/order-intents/{intentId}
```

하위 리소스는 부모 리소스 아래에 둔다.

```http
GET /api/orders/{orderId}/fills
GET /api/orders/{orderId}/events
GET /api/portfolios/{portfolioId}/positions
```

### 특수한 실행 결과는 리소스처럼 이름 붙인다

replay, consistency report처럼 단순 CRUD가 아닌 API도 가능하면 명사로 표현한다.

```http
GET /api/audit-replay/order-replay/stored-orders/{orderId}
GET /api/audit-replay/order-replay/consistency-report
GET /api/audit-replay/order-replay/consistency/{orderId}
```

여기서 `consistency-report`는 "리포트를 조회한다"는 리소스 관점이고, `checkConsistency` 같은 동사형 API보다 문서화와 확장이 쉽다.

### source 구분은 URL 또는 필드 중 하나를 명확히 선택한다

주문 의도 생성처럼 같은 리소스라도 입력 출처가 다르면 두 가지 방식이 가능하다.

방식 A: URL로 source를 분리한다.

```http
POST /api/order-intents/manual
POST /api/order-intents/rebalancing
POST /api/order-intents/strategy
```

방식 B: 하나의 URL에서 request body의 `sourceType`으로 구분한다.

```http
POST /api/order-intents
```

현재 프로젝트는 source별 request 필드가 다르다. 예를 들어 manual은 원본 참조 ID가 없고, rebalancing은 `rebalanceRunId`, strategy는 `strategySignalId`가 있다. 그래서 MVP 단계에서는 방식 A가 더 읽기 쉽다.

나중에 source가 많아지고 request 계약이 거의 같아지면 방식 B로 통합하는 것을 검토할 수 있다.

## Request 설계

### API request DTO와 domain model을 구분한다

controller는 외부 입력을 받는 경계다. 이 경계에서는 API 요청 DTO를 사용하고, 내부에서는 command나 domain model로 변환한다.

```java
public record ManualOrderIntentRequest(
        String portfolioId,
        String instrumentId,
        OrderSide side,
        OrderType orderType,
        BigDecimal requestedQty,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        String reason,
        String idempotencyKey,
        String createdBy
) {
}
```

서비스 내부에서는 request를 바로 저장하지 않고 공통 command로 변환한다.

```java
OrderIntent intent = orderIntentFactory.create(new CreateOrderIntentCommand(
        request.portfolioId(),
        request.instrumentId(),
        OrderIntentSourceType.MANUAL,
        null,
        request.side(),
        request.orderType(),
        request.requestedQty(),
        request.limitPrice(),
        request.timeInForce(),
        request.reason(),
        request.idempotencyKey(),
        request.createdBy()
));
```

이렇게 하면 API 입력 계약이 바뀌어도 domain 생성 규칙은 `OrderIntentFactory`에 남길 수 있다.

### 멱등성이 필요한 생성 API는 idempotencyKey를 받는다

주문 생성 계열 API는 네트워크 재시도와 중복 클릭이 자연스럽게 발생한다. 같은 요청이 두 번 들어왔을 때 중복 주문이 생기면 위험하다.

따라서 생성 API는 가능한 한 `idempotencyKey`를 받는다.

```json
{
  "portfolioId": "portfolio-1",
  "instrumentId": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "requestedQty": 10,
  "limitPrice": 55000,
  "timeInForce": "DAY",
  "idempotencyKey": "manual-20260602-0001",
  "createdBy": "operator"
}
```

현재 생성 API는 같은 `idempotencyKey`와 같은 요청 내용이 다시 들어오면 새 주문 의도를 만들지 않고 최초 생성된 `OrderIntent`를 반환한다. 이 정책은 네트워크 재시도, 브라우저 중복 클릭, worker 재처리 상황에서 중복 주문을 줄이기 위한 기본 방어선이다.

반대로 같은 `idempotencyKey`인데 요청 내용이 다르면 재시도가 아니라 key 재사용 충돌로 보고 `409 Conflict`를 반환한다. 조용히 기존 결과를 반환하면 호출자는 전혀 다른 주문이 생성됐다고 오해할 수 있기 때문이다.

같은 요청 내용이라도 `idempotencyKey`가 다르면 새 요청으로 본다. 같은 종목/수량 주문을 의도적으로 여러 번 낼 수 있기 때문이다. 이런 케이스의 중복 주문 의심 여부는 idempotency가 아니라 duplicate order detection이나 pre-trade risk rule에서 별도로 판단한다.

### 금액과 수량은 문자열 또는 숫자 정책을 일관되게 둔다

Java에서는 `BigDecimal`을 사용한다. JSON에서는 현재 테스트와 문서에서 숫자 형태를 사용하되, 소수 정밀도가 중요한 자산으로 확장할 때 문자열 입력도 검토한다.

```json
{
  "requestedQty": 10,
  "limitPrice": 55000
}
```

금융 도메인에서는 `double`/`float` 기반 계산을 피한다.

## Response 설계

### 생성 API는 생성된 리소스 스냅샷을 반환한다

생성 API는 `201 Created`와 함께 생성된 리소스의 현재 상태를 반환한다.

```http
POST /api/order-intents/manual
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
  "createdAt": "2026-06-02T00:00:00Z",
  "updatedAt": "2026-06-02T00:00:00Z"
}
```

생성 직후의 상태까지 내려주면 호출자는 별도 조회 없이 다음 단계로 이어갈 수 있다.

### 목록/리포트 응답은 집계와 상세를 분리한다

리포트 API는 전체 요약 필드와 개별 결과 목록을 함께 내려준다.

```json
{
  "totalCount": 3,
  "consistentCount": 1,
  "inconsistentCount": 2,
  "inconsistentRatio": 0.6667,
  "results": [],
  "checkedAt": "2026-06-02T00:00:00Z"
}
```

운영 화면에서는 상단에는 집계를 보여주고, 아래에는 상세 목록을 보여주는 경우가 많다. 응답 구조도 이 사용 방식을 반영한다.

## Status Code

프로젝트에서 기본으로 사용할 HTTP 상태 코드는 다음과 같다.

| Status | 의미 | 사용 예 |
| --- | --- | --- |
| `200 OK` | 조회 또는 처리 성공 | report 조회, replay 조회 |
| `201 Created` | 리소스 생성 성공 | order intent 생성 |
| `400 Bad Request` | 요청 값 검증 실패 | LIMIT 주문에 limitPrice 누락 |
| `404 Not Found` | 대상 리소스 없음 | 없는 orderId 조회 |
| `409 Conflict` | 현재 상태와 충돌 | 이미 취소된 주문을 다시 체결 처리 |
| `500 Internal Server Error` | 예상하지 못한 서버 오류 | 방어하지 못한 예외 |

검증 실패와 상태 충돌은 구분한다.

예를 들어 `requestedQty <= 0`은 요청 값 자체가 잘못된 것이므로 `400`이 맞다. 반면 `FILLED` 주문을 취소하려는 요청은 문법은 맞지만 현재 상태와 충돌하므로 `409`가 더 적합하다.

## Error Response

에러 응답은 최소한 `message`를 포함한다.

```json
{
  "message": "limitPrice is required for LIMIT orders"
}
```

운영 진단성이 더 필요해지면 다음 필드를 추가한다.

```json
{
  "code": "LIMIT_PRICE_REQUIRED",
  "message": "limitPrice is required for LIMIT orders",
  "path": "/api/order-intents/manual",
  "occurredAt": "2026-06-02T00:00:00Z"
}
```

단, 에러 응답 필드를 확장할 때는 모든 API 모듈의 exception handler를 한 번에 맞추는 편이 좋다. 모듈마다 에러 모양이 다르면 운영 화면과 테스트 코드가 복잡해진다.

## Controller 작성 기준

controller는 얇게 유지한다.

```java
@RestController
@RequestMapping("/api/order-intents/rebalancing")
public class RebalancingOrderIntentController {

    private final RebalancingOrderIntentService rebalancingOrderIntentService;

    public RebalancingOrderIntentController(RebalancingOrderIntentService rebalancingOrderIntentService) {
        this.rebalancingOrderIntentService = rebalancingOrderIntentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderIntent create(@RequestBody RebalancingOrderIntentRequest request) {
        return rebalancingOrderIntentService.create(request);
    }
}
```

controller에서 해야 할 일:

- HTTP path, method, status code를 정의한다.
- request body, path variable, query parameter를 받는다.
- application service를 호출한다.
- service 결과를 response로 반환한다.

controller에서 피할 일:

- 주문 상태 전이 규칙을 직접 구현한다.
- repository를 직접 호출한다.
- 복잡한 계산을 수행한다.
- 모듈 밖 domain을 과하게 조합한다.

## Query Parameter 기준

query parameter는 조회 범위를 좁히거나 조회 옵션을 바꿀 때 사용한다.

```http
GET /api/audit-replay/order-replay/consistency-report?inconsistentOnly=true
```

`inconsistentOnly`는 report 리소스 자체를 바꾸지 않고 조회 결과 필터만 바꾼다. 그래서 query parameter가 자연스럽다.

반대로 반드시 있어야 하는 식별자는 path variable로 둔다.

```http
GET /api/audit-replay/order-replay/consistency/{orderId}
```

## 테스트 기준

API를 추가하면 최소한 controller 테스트를 둔다.

```java
@WebMvcTest(RebalancingOrderIntentController.class)
@ContextConfiguration(classes = {
        RebalancingOrderIntentControllerTest.TestApplication.class,
        RebalancingOrderIntentController.class,
        OrderIntentExceptionHandler.class
})
class RebalancingOrderIntentControllerTest {
}
```

기본 검증 항목:

- 정상 요청이 기대한 HTTP status를 반환하는가
- response JSON의 핵심 필드가 맞는가
- service에서 발생한 검증 예외가 API 에러 응답으로 변환되는가
- path variable/query parameter 이름이 문서와 테스트에서 일치하는가

테스트는 controller 내부 구현보다 HTTP 계약을 고정하는 데 초점을 둔다.

## 문서화 기준

새 API가 생기면 다음 중 하나를 업데이트한다.

- API별 사용 가이드가 필요하면 `docs/{module-name}-api.md`
- 공통 원칙이 바뀌면 이 문서
- slice 작업 이력은 `HISTORY.md`
- README에서 바로 찾아야 하는 주요 문서는 README 링크

API 문서에는 최소한 다음을 포함한다.

- endpoint
- request 예시
- response 예시
- 주요 필드 의미
- 오류 케이스
- 운영자가 언제 쓰는 API인지

## 현재 프로젝트 적용 상태

현재 적용된 REST API 흐름은 다음과 같다.

| Module | Endpoint | 목적 |
| --- | --- | --- |
| `intent-generation` | `POST /api/order-intents/manual` | 수동 주문 의도 생성 |
| `intent-generation` | `POST /api/order-intents/rebalancing` | 리밸런싱 주문 의도 생성 |
| `intent-generation` | `POST /api/order-intents/strategy` | 전략 신호 주문 의도 생성 |
| `intent-generation` | `GET /api/order-intents/{intentId}` | 주문 의도 단건 조회 |
| `intent-generation` | `GET /api/order-intents?idempotencyKey=...` | idempotency key 기준 주문 의도 조회 |
| `market-data` | `PUT /api/market-data/instruments/{instrumentId}/prices/latest` | instrument 최신 시장 가격 저장 |
| `market-data` | `GET /api/market-data/instruments/{instrumentId}/prices/latest` | instrument 최신 시장 가격 조회 |
| `pre-trade-risk` | `POST /api/pre-trade-risk/order-intents/{intentId}/evaluations` | 주문 의도 사전 리스크 평가 |
| `execution` | `POST /api/order-intents/{intentId}/orders` | risk 승인 주문 의도를 order로 변환 |
| `execution` | `POST /api/orders/{orderId}/submissions` | 생성된 order 제출 처리 |
| `execution` | `POST /api/orders/{orderId}/acknowledgements` | broker/exchange ACK 반영 |
| `execution` | `POST /api/orders/{orderId}/rejections` | broker/exchange reject 반영 |
| `execution` | `POST /api/orders/{orderId}/fills` | broker/exchange fill 반영 |
| `execution` | `POST /api/orders/{orderId}/cancel-requests` | order 취소 요청 처리 |
| `execution` | `POST /api/orders/{orderId}/cancel-confirmations` | broker/exchange cancel confirmation 반영 |
| `post-trade` | `POST /api/post-trade/orders/{orderId}/trades` | execution order를 post-trade trade로 capture |
| `post-trade` | `POST /api/post-trade/trades/{tradeId}/settlements` | trade settlement 예정 등록 |
| `post-trade` | `POST /api/post-trade/settlements/{settlementId}/confirmations` | settlement 완료 확인 |
| `post-trade` | `POST /api/post-trade/trades/{tradeId}/ledger-postings` | settled trade를 position/cash ledger에 posting |
| `post-trade` | `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}` | portfolio/instrument 현재 position 조회 |
| `post-trade` | `GET /api/post-trade/portfolios/{portfolioId}/cash` | portfolio 현재 cash 조회 |
| `post-trade` | `POST /api/post-trade/trades/{tradeId}/realized-pnl-postings` | settled SELL trade의 realized PnL posting |
| `post-trade` | `GET /api/post-trade/portfolios/{portfolioId}/realized-pnl` | portfolio 현재 누적 realized PnL 조회 |
| `post-trade` | `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl` | 현재 position 기준 unrealized PnL snapshot 조회 |
| `post-trade` | `GET /api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest` | latest market price 기준 unrealized PnL snapshot 조회 |
| `audit-replay` | `GET /api/audit-replay/order-replay/consistency-report` | 전체 replay consistency report 조회 |
| `audit-replay` | `GET /api/audit-replay/order-replay/consistency/{orderId}` | 단건 consistency 조회 |
| `audit-replay` | `GET /api/audit-replay/order-replay/stored-orders/{orderId}` | 저장된 order 기준 execution replay 조회 |
| `audit-replay` | `GET /api/audit-replay/order-replay/{orderId}` | 명시 수량 기준 execution replay 조회 |
| `audit-replay` | `GET /api/audit-replay/order-audit-trails/{orderId}` | 주문 audit trail 조회 |

새 API를 추가할 때는 이 표와 충돌하지 않는 이름을 먼저 고른다.
