// Gradle 플러그인을 어디서 내려받을지 정의
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 모든 모듈의 라이브러리 저장소를 루트에서 일괄 관리한다.
// 각 모듈이 개별 repositories 를 선언하지 못하게 막아 설정을 단순화
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "multi-asset-oms-simulation-platform"

// Gradle 멀티모듈 구조
// 아래 이름이 그대로 서브프로젝트 경계가 됨
include(
    "market-data",
    "intent-generation",
    "pre-trade-risk",
    "oms-core",
    "execution",
    "post-trade",
    "audit-replay",
)
