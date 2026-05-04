plugins {
    // execution 은 독립 실행 앱이 아니라 다른 모듈이 가져다 쓰는 라이브러리 모듈
    `java-library`
}

dependencies {
    // execution 계층은 시장데이터 모델을 입력으로 사용하므로 market-data 에 의존
    // 현재는 API 타입 노출을 열어 둔 상태지만, 구현이 쌓이면 implementation 축소를 검토
    api(project(":market-data"))
    // Spring Bean 구성과 이벤트/서비스 wiring 을 위해 spring-context 를 사용
    api("org.springframework:spring-context")
}
