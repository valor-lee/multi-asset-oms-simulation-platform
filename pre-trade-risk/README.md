# pre-trade risk

`OrderIntent`를 거래소 주문으로 보내기 전에 수량, 금액, 포지션 등 사전 리스크 규칙을 평가하는 모듈.

API 사용법은 [Pre-Trade Risk API Guide](../docs/pre-trade-risk-api.md)에 기록한다.

## Duplicate open order

`idempotencyKey`는 같은 생성 요청의 재시도를 막는 장치이고, 같은 payload에 다른 key가 붙어 들어온 주문은 별도 요청으로 처리된다.

따라서 "같은 종목/방향/수량/가격의 open order가 이미 있는가"는 pre-trade risk의 `DUPLICATE_OPEN_ORDER` 규칙에서 판단한다.

현재 `PreTradeRiskOpenOrderContext`는 다음 값을 받는다.

| Field | Meaning |
| --- | --- |
| `duplicateOpenOrderExists` | 같은 주문으로 의심되는 open order 존재 여부 |
| `duplicateOpenOrderId` | 매칭된 open order id. 있으면 rule result의 `evaluatedValue`에 기록된다. |

실제 open order 조회는 아직 저장소와 연결하지 않고, 호출자가 조회한 결과를 context로 전달하는 계약만 고정한다.

slice 작업 이력은 루트 [HISTORY.md](../HISTORY.md#pre-trade-risk)에 기록한다.
