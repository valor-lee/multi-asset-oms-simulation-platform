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

### 2026.04.30 slice

전체 승인/거절 결과뿐 아니라 규칙별 검사 결과를 함께 반환하도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskRuleCode` 추가
  - `POSITIVE_QUANTITY`, `LIMIT_PRICE_REQUIRED`, `POSITIVE_LIMIT_PRICE`
- `PreTradeRiskRuleStatus` 추가
  - `PASSED`, `FAILED`, `SKIPPED`
- `PreTradeRiskRuleCheckResult` 추가
  - 규칙 코드, 상태, 메시지, 평가값, 기준값을 표현
- `PreTradeRiskCheckResult`에 `ruleResults` 추가
- `PreTradeRiskCheckService`가 개별 규칙 결과를 모아 전체 decision을 산출하도록 변경
- 규칙별 결과 상태 테스트 추가

#### 메모

- 현재 규칙은 코드 기반으로 고정되어 있지만, 구조는 문서의 `RiskRule` / `RiskCheckResult` 설계로 확장 가능하게 둔다.
- 하나 이상의 규칙이 `FAILED`이면 전체 decision은 `REJECTED`가 된다.

### 2026.05.01 slice

외부 한도값을 받아 주문 수량 한도 규칙을 평가할 수 있도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext` 추가
  - 현재는 `maxOrderQty`만 포함
- `MAX_ORDER_QUANTITY` 규칙 추가
- `PreTradeRiskCheckService.check(command, limitContext)` 추가
- 기존 `check(command)`는 한도값이 없는 기본 컨텍스트로 동작하도록 유지
- 최대 주문 수량 이하/초과/미설정 케이스 테스트 추가

#### 현재 검사 규칙 추가

- `maxOrderQty`가 없으면 `MAX_ORDER_QUANTITY`는 `SKIPPED`
- `requestedQty`가 `maxOrderQty` 이하이면 `PASSED`
- `requestedQty`가 `maxOrderQty`를 초과하면 `FAILED`, 전체 decision은 `REJECTED`

### 2026.05.04 slice

주문 수량 한도에 이어 주문 금액 한도 규칙을 평가할 수 있도록 확장.

#### 이번 슬라이스에서 한 일

- `PreTradeRiskLimitContext`에 `maxOrderNotional` 추가
- `MAX_ORDER_NOTIONAL` 규칙 추가
- `requestedQty * limitPrice`로 주문 금액을 계산해 한도 이하/초과를 평가
- 기존 `PreTradeRiskLimitContext(maxOrderQty)` 생성 경로는 유지
- 주문 금액 한도 이하/초과/미설정 케이스 테스트 추가

#### 현재 검사 규칙 추가

- `maxOrderNotional`이 없으면 `MAX_ORDER_NOTIONAL`은 `SKIPPED`
- 주문 금액을 계산할 수 없으면 `MAX_ORDER_NOTIONAL`은 `SKIPPED`
- 주문 금액이 `maxOrderNotional` 이하이면 `PASSED`
- 주문 금액이 `maxOrderNotional`을 초과하면 `FAILED`, 전체 decision은 `REJECTED`
