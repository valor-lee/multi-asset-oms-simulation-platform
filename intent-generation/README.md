# intent generation

## 작업 이력

### 2026.04.25 slice

공통 `OrderIntent` 모델과 기본 생성 API를 만들고, 다른 모듈이 참조 가능한 안정된 계약부터 세우는 방향으로 진행.

#### 이번 슬라이스에서 한 일

- `OrderIntent`, `CreateOrderIntentCommand`를 추가해 주문 의도의 공통 입력/출력 모델을 정의
- `OrderIntentSourceType`, `OrderSide`, `OrderType`, `TimeInForce`, `OrderIntentStatus` enum 추가
- `OrderIntentFactory`를 추가해 주문 의도 생성 책임을 한곳으로 모음
- `manual`, `rebalancing`, `strategy` 경로별 request/service를 만들고 공통 factory를 사용하도록 연결
- `OrderIntentFactoryTest`를 추가해 핵심 생성 규칙을 테스트로 고정

#### 생성 규칙

- 필수값 검증은 Jakarta Validation 기반으로 수행
- `LIMIT` 주문은 `limitPrice` 필수
- `MARKET` 주문은 `limitPrice` 금지
- `idempotencyKey`가 비어 있으면 기본 UUID 생성
- 생성 시 초기 상태는 `CREATED`
- `createdAt`, `updatedAt`는 생성 시점으로 동일하게 세팅

#### 현재 코드 구조

- `model`
  주문 의도 공통 모델과 enum
- `application`
  `OrderIntentFactory`
- `manual`
  수동 주문 의도 요청/생성 서비스
- `rebalancing`
  리밸런싱 주문 의도 요청/생성 서비스
- `strategy`
  전략 신호 기반 주문 의도 요청/생성 서비스

#### 검증

- 실행 테스트: `./gradlew :intent-generation:test`
- 현재 테스트 범위
  - 정상적인 `LIMIT` 주문 생성
  - `idempotencyKey` 자동 생성
  - `LIMIT` 주문의 `limitPrice` 누락 거절
  - `MARKET` 주문의 `limitPrice` 포함 거절

#### 메모

- 현재 `intentId`는 도메인 식별자로 `UUID`를 사용
  - 아직 JPA 엔터티/DB 시퀀스 전략이 정해지지 않은 단계에서도 생성 시점에 바로 식별자를 부여할 수 있음
  - 메모리 단계, 테스트, 로그, 이후 이벤트 연결에서 즉시 참조 가능한 ID가 필요함
  - 여러 모듈이나 흐름에서 생성되더라도 충돌 가능성이 매우 낮음
  - 영속화 전략이 확정되기 전까지 도메인 모델을 독립적으로 다루기 쉬움
- 문서상의 DB 스키마와 영속화 방식은 이후 JPA 엔터티 설계 시 구체화 예정
- 다음 슬라이스에서는 `pre-trade-risk` 입력 계약 연결 또는 manual input API 연결을 고려
