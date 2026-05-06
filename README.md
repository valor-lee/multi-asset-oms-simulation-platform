# Multi-Asset OMS Simulation Platform

멀티자산 주문관리시스템(OMS)과 자동주문 시뮬레이션을 위한 프로젝트입니다.

포트폴리오 리밸런싱, 전략 기반 자동주문, 수동 주문을 **하나의 공통 주문 파이프라인으로 처리**하며, 주문 상태 관리, 리스크 검증, 체결 시뮬레이션, 후처리, 감사 추적까지 포함하는 금융거래 엔진을 목표로 합니다.

slice 작업 이력은 [HISTORY.md](HISTORY.md)에 모아 기록합니다.

## Overview

금융산업 프로세스를 아래 다섯 단계로 나눠볼 수 있습니다.

### 1) 투자 의도 생성

맨 앞단에서는 PM이나 전략 로직이 “무엇을 얼마나 사고팔 것인가”를 결정한다. 

이 단계의 입력은 목표 비중, 현재 포지션, 현금, 시장 데이터, 전략 신호 같은 것들이고, 출력은 아직 거래소 주문이 아닌 주문 의도에 가깝다. 

상용 투자 플랫폼도 이 구간을 포트폴리오 관리·리스크 분석과 연결된 “proposed order” 영역으로 본다.

### 2) 사전 리스크/컴플라이언스 검증

의도가 곧바로 시장으로 나가지는 않는다. 

포지션 한도, 종목 제한, 가격 제한, 계좌/상품 제약, 투자 규정 준수 여부를 먼저 본다.

상용 컴플라이언스 시스템도 보유 포지션뿐 아니라 pre-trade, in-trade, post-execution 전 단계에서 규칙을 검사하고, 히스토리와 감사 이력을 남기는 것을 핵심 기능으로 둔다.

### 3) 주문 관리와 주문 라우팅

검증을 통과하면 OMS가 실제 주문 객체를 만들고, 수정·취소·재전송·중복 방지·상태 전이를 관리한다. 

이때 외부 브로커/거래소와 주고받는 인터페이스는 현실적으로 FIX 같은 표준 메시지 체계나 증권사 Open API 형태로 나타난다. 

FIX는 증권 거래 주기의 단계를 반영하는 메시지 표준이고, 국내 증권사 Open API도 현금주식 주문, 정정취소, 일별 주문체결 조회, 잔고조회, 선물옵션 주문, 해외주식 주문, 장내채권 주문 등으로 기능이 나뉘어 있다. 

즉, 실제 산업도 “시세/주문/체결/잔고”를 분리된 책임으로 다룬다.

### 4) 거래소/시장 체결

주문이 시장으로 가면 시장 마이크로구조에 따라 체결된다. 

한국거래소 가이드는 연속매매에서는 새로운 주문이 가격·시간 우선 원칙에 따라 즉시 체결되고, 장 개시/종가 등은 단일가 매매로 처리되며, 체결 전에는 정정·취소가 가능하다고 설명한다. 

즉 시스템 입장에서는 “주문 넣으면 바로 체결”이 아니라, 시장 규칙에 따라 ACK, 미체결, 부분체결, 체결, 취소, 거절 같은 상태가 발생한다.

### 5) 사후 처리와 운영

체결되면 끝이 아니라 포지션, 평균단가, 현금, 손익, 주문/체결 이력, 규제 대응용 감사 로그가 남아야 한다. 

상용 플랫폼도 post-trade processing, settlement, accounting, reconciliation, real-time cash and positions를 별도 영역으로 둔다. 

컴플라이언스도 과거 시점 기준 스냅샷과 완전한 감사 이력을 중요 기능으로 둔다.

 ---

이 프로젝트는 다음과 같은 실무형 문제를 다루기 위해 설계됩니다.

- 여러 주문 소스를 하나의 OMS 코어에서 일관되게 처리
- 주문 상태 전이와 이벤트 저장을 통한 정합성 확보
- 사전 리스크 체크와 체결 후 정산 로직의 분리
- 부분체결, 취소 경쟁, 중복 요청 등 실제 운영 이슈 대응
- 이벤트 로그 기반 재현, 감사 추적, 장애 복구 검증

본 프로젝트로 아래의 요소를 중요하게 고민하고 해결해보고자 합니다.

- 정합성 보장
- 상태 관리
- 동시성 제어
- 감사 추적
- 장애 복구
- 성능 측정

## Goals

- 리밸런싱, 전략 엔진, 수동 입력 등 다양한 주문 소스를 `OrderIntent` 형태로 통합한다.
- OMS 코어에서 주문 생성, 상태 전이, 이벤트 저장을 일관되게 처리한다.
- 리스크 엔진을 독립 모듈로 설계해 사전 검증을 수행한다.
- 체결 시뮬레이터를 통해 ACK, 부분체결, 체결, 취소, 거절 등의 이벤트를 재현한다.
- 후처리 엔진에서 포지션, 잔고, 평균단가, 손익을 정합하게 반영한다.
- 동일 이벤트 로그를 사용해 리플레이 및 복구 검증이 가능하도록 한다.

## System Flow

```text
Market Data
    -> Manual Input / Strategy / Rebalancing
    -> OrderIntent 생성
    -> Pre-Trade Risk
    -> OMS Core
       - 주문 생성
       - 상태 전이
       - 이벤트 저장
    -> Execution
    -> Post-Trade
       - 포지션 반영
       - 잔고 반영
       - PnL 계산
    -> Audit / Replay
```

## MVP Scope

### 1차 MVP

**지원 자산**

- 국내 주식
- 현금

**주문 유형**

- 시장가 주문
- 지정가 주문

**주문 소스**

- 수동 주문
- 포트폴리오 리밸런싱 주문
- 단일 자동매매 전략

**리스크 체크**

- 종목별 최대 주문 수량 제한
- 최대 주문 금액 제한
- 총 포지션 한도 제한
- 중복 주문 방지
- 가격 밴드 체크
- Kill Switch

**체결 시뮬레이션**

- `ACK`
- `PARTIAL_FILL`
- `FILL`
- `CANCEL`
- `REJECT`
- 지연 및 슬리피지 반영

**후처리**

- 포지션 반영
- 평균단가 계산
- 실현손익 / 평가손익 계산
- 주문 / 체결 감사 로그 저장

**리플레이**

- 동일 이벤트 기반 재현 가능

### 1차 MVP 기술 스택

#### 백엔드

- Java 21
- Spring Boot 3.x
- Spring Modulith
- Spring Web / Validation / Actuator

Java 21은 LTS 버전이고, Spring Boot 3.2 계열은 Java 21까지 호환된다. Spring Modulith는 Spring Boot 애플리케이션을 도메인 중심 모듈로 구조화하고, 모듈 간 상호작용을 이벤트 중심으로 정리하는 데 초점이 있다. 이 프로젝트처럼 `market-data`, `pre-trade-risk`, `oms-core`, `execution`, `post-trade`의 책임 경계가 중요한 구조에 잘 맞는다.

#### DB / 영속성

- PostgreSQL
- Spring Data JPA + Hibernate
- 조회가 복잡한 운영 화면은 JdbcTemplate 또는 Querydsl 보조 사용

PostgreSQL은 강한 트랜잭션 모델을 갖고 있고 JSON/JSONB를 지원해서 `order_event.payload_json` 같은 이벤트 payload 저장에 잘 맞는다. 또 PostgreSQL은 명시적 락과 트랜잭션 격리 수준을 제공하고, Spring Data JPA는 `@Lock` 기반 잠금 메타데이터를 지원한다. 이 조합이면 `Order`, `PortfolioPosition`, `CashBalance`의 버전 충돌과 상태 전이 정합성을 다루기 좋다.

#### 빌드 / 멀티모듈

- Gradle Kotlin DSL
- 패키지보다 모듈 경계 우선

Gradle은 Java 플러그인 기반 빌드에 적합하고, Spring Boot 샘플도 Gradle 기반 구성을 공식적으로 제공한다. 이 프로젝트는 처음부터 멀티모듈 또는 최소한 강한 모듈 경계가 중요하므로 Gradle 쪽이 관리하기 편하다.

#### 운영자 UI

- React
- TypeScript
- Vite

`admin-dashboard`는 핵심 처리 계층이 아니라 운영자용 인터페이스이므로, 빠르게 화면을 만들 수 있는 React가 적합하다. React는 컴포넌트 기반 UI 구성에 강하고, Vite는 빠른 프론트엔드 개발 도구다.

#### 개발 환경

- Docker Compose
- 로컬에서 app + postgres + redis(optional) + prometheus + grafana 실행

Docker Compose는 멀티 컨테이너 애플리케이션을 정의하고 실행하는 공식 도구다. 로컬 개발환경을 빠르게 재현하기 좋다.

#### 테스트

- JUnit 5
- Testcontainers
- 성능 테스트는 k6

Testcontainers는 데이터베이스 같은 실제 의존성을 일회성 컨테이너로 띄워 통합 테스트를 수행하게 해준다. 이 프로젝트는 체결, 후처리, 리플레이처럼 H2로는 놓치기 쉬운 케이스가 많아서 실제 PostgreSQL 기반 테스트가 중요하다. k6는 API 부하 테스트용 OSS 도구다.

#### 관측성

- Micrometer
- Prometheus
- Grafana

Micrometer는 Spring Boot 관측성 계층의 핵심이고, Prometheus는 시계열 메트릭 수집, Grafana는 대시보드 시각화에 적합하다. 주문 처리 지연, fill 이벤트 수, 리스크 거절 수, replay 시간 같은 지표를 보기에 좋다.

### 2차 확장

- ETF, 채권형 상품, 가상자산 모사형 instrument type 확장
- 브로커 Open API 어댑터 연동
- 일손실 한도 관리
- 다중 전략 지원
- 성능 대시보드 강화
- 이벤트 재생 기반 장애 복구 검증

## Core Domain Model

| Entity | Description |
| --- | --- |
| `Instrument` | 종목 정보. `asset_type`, `ticker`, `exchange`, `tick_size`, `lot_size` 등을 포함 |
| `Portfolio` | 계좌/포트폴리오 단위 상태. 현금, 목표 비중, 현재 포지션 관리 |
| `OrderIntent` | 주문 의도. 전략, 리밸런싱, 수동 입력이 먼저 생성하는 공통 표현 |
| `Order` | OMS가 실제로 관리하는 주문 객체. 상태 머신의 대상 |
| `Execution` | 체결 결과. 부분체결이 여러 건 발생할 수 있음 |
| `OrderEvent` | 상태 전이 및 이벤트 로그. 감사 추적과 리플레이의 핵심 데이터 |

## Order State Machine

### 상태 목록

- `CREATED`
- `RISK_APPROVED`
- `RISK_REJECTED`
- `SENT`
- `ACKED`
- `PARTIALLY_FILLED`
- `FILLED`
- `CANCEL_REQUESTED`
- `CANCELED`
- `REJECTED`

### 기본 전이

```text
CREATED -> RISK_APPROVED
CREATED -> RISK_REJECTED
RISK_APPROVED -> SENT
SENT -> ACKED
ACKED -> PARTIALLY_FILLED
ACKED -> FILLED
PARTIALLY_FILLED -> FILLED
ACKED -> CANCEL_REQUESTED
PARTIALLY_FILLED -> CANCEL_REQUESTED
CANCEL_REQUESTED -> CANCELED
```

### 주요 예외 케이스

- `CANCEL_REQUESTED` 이후 `FILL` 이벤트가 먼저 도착할 수 있는 cancel-fill race condition
- `PARTIALLY_FILLED` 상태에서 추가 주문 생성 시 중복 주문 위험
- 동일 요청 재전송 시 `idempotency_key` 기반 중복 방지 필요

## Risk Engine

리스크 엔진은 OMS와 분리된 독립 검증 모듈로 설계합니다.

### 1차 규칙

- `maxOrderQty`
- `maxOrderNotional`
- `maxPositionQty`
- `duplicateOpenOrderCheck`
- `priceBandCheck`
- `killSwitchCheck`

## Strategy Engine

자동매매는 별도 주문 시스템이 아니라 `OrderIntent` 생성기로 동작합니다.

### 역할

- 시장 데이터 수신
- 전략 신호 생성
- 주문 직접 전송 대신 `OrderIntent` 생성
- 이후 단계는 OMS 공통 파이프라인 재사용

### 초기 전략 후보

- 이동평균선 크로스
- 변동성 돌파
- 목표 비중 유지형 모멘텀

## Rebalancing Engine

### 입력

- 목표 비중
- 현재 보유 수량
- 현금
- 최신 가격

### 출력

- 매수/매도 대상 종목
- 종목별 주문 수량
- `OrderIntent` 목록

## Execution Simulator

### 지원 기능

- ACK 지연
- 부분체결
- 전량체결
- 슬리피지 반영
- 거래량 부족에 따른 미체결
- 취소 성공/실패 시뮬레이션

### 예시 정책

- 시장가 주문: 현재가 기준 슬리피지 반영 체결
- 지정가 주문: 가격 조건 충족 시 체결
- 대량 주문: 분할 체결
- 네트워크 지연: 랜덤 `20~200ms`
- 브로커 오류: 일정 확률로 `REJECT`

## Post-Trade Processing

체결 이후 결과를 정합하게 반영하는 후처리 엔진입니다.

### 반영 항목

- 포지션 수량
- 평균 매입단가
- 현금 증감
- 실현손익
- 평가손익
- 일별 손익 스냅샷

## Package Structure

```text
├─ market-data
├─ intent-generation
│  ├─ manual-input
│  ├─ rebalancing
│  └─ strategy
├─ pre-trade-risk
├─ oms-core
│  ├─ domain
│  ├─ application
│  ├─ infra
│  └─ api
├─ execution
├─ post-trade
├─ audit-replay
└─ admin-dashboard
```

## Development Roadmap

### 1주차

- 요구사항 문서화
- 도메인 모델 확정
- 주문 상태 머신 정의
- DB 스키마 초안 작성
- 프로젝트 골격 생성

### 2주차

- `OrderIntent -> RiskEngine -> Order` 흐름 구현
- 주문 상태 전이 로직 구현
- `order_events` 저장 구조 구현

### 3주차

- 체결 시뮬레이터 구현
- partial fill / cancel / reject 처리
- position / cash / pnl 반영

### 4주차

- 포트폴리오 리밸런싱 엔진 구현
- 수동 주문 API 구현
- 기본 대시보드 구현

### 5주차

- 자동매매 전략 엔진 1개 추가
- market replay 기능 구현
- 리플레이 기반 재현 테스트

### 6주차

- 성능 측정
- 장애/경합 테스트
- README / 포트폴리오 문서 / 아키텍처 문서 정리

## Expected Outcomes

이 프로젝트를 통해 다음과 같은 시스템 설계 및 구현 경험을 보여줄 수 있습니다.

- 주문관리시스템(OMS) 아키텍처 이해
- 상태 머신 기반 주문 라이프사이클 관리
- 이벤트 소싱 성격의 감사 로그 및 리플레이 설계
- 리스크 관리 모듈 분리 설계
- 체결 시뮬레이션 및 후처리 정합성 검증
- 금융 시스템에서의 동시성 및 장애 대응 시나리오 처리
