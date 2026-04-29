# pre-trade risk

## 작업 이력

### 2026.04.29 slice

`intent-generation`에서 생성된 `OrderIntent`를 사전 리스크 검사 입력으로 받을 수 있도록 기본 계약을 세움.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskCheckCommand` 추가
  - `OrderIntent`에서 리스크 검사에 필요한 기본 필드를 추출
- `PreTradeRiskDecision` 추가
  - `APPROVED`, `REJECTED`
- `PreTradeRiskCheckResult` 추가
  - 검사 대상 intent, decision, reason, checkedAt 반환
- `PreTradeRiskCheckService` 추가
  - 현재는 입력 계약을 고정하기 위한 최소 방어 규칙만 검사
- `PreTradeRiskCheckServiceTest` 추가

#### 현재 검사 규칙

- `requestedQty`가 없거나 0 이하이면 `REJECTED`
- `LIMIT` 주문인데 `limitPrice`가 없으면 `REJECTED`
- `limitPrice`가 0 이하이면 `REJECTED`
- 그 외는 `APPROVED`

#### 메모

- `OrderIntentFactory`에서 이미 기본 주문 생성 규칙을 검증한다는 전제를 둔다.
- 현재 단계에서는 계좌, 포지션, 주문 한도, 시장 가격 기반 notional 계산은 아직 수행하지 않는다.

#### 다음 후보

- portfolio/account exposure 입력 모델 추가
- 주문 금액 한도 검사
- risk 결과를 intent 상태 전이와 연결
