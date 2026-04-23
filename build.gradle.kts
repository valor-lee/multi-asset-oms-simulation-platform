plugins {
    // 루트도 Java 프로젝트 메타데이터를 갖도록 java 플러그인을 선언
    java
    // Spring Boot 플러그인은 필요한 실행 모듈에서만 켜기 위해 apply false 로 둠
    id("org.springframework.boot") version "3.2.12" apply false
    // Spring BOM 기반 버전 관리를 위해 공통 등록
    id("io.spring.dependency-management") version "1.1.6" apply false
}

// 모든 모듈이 공통으로 사용할 group / version
group = "com.multiassetoms"
version = "0.1.0-SNAPSHOT"

// Modulith 버전을 변수로 빼 두면 루트에서 일괄 업그레이드하기 쉬움
val springModulithVersion = "1.1.10"

// 서브모듈 전체에 공통 빌드 규칙을 적용
subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            // 로컬 기본 JDK와 무관하게 Java 21 기준으로 빌드
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // BOM을 가져와 Spring / Modulith / Testcontainers 의존성 버전을 중앙 관리
    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.12")
            mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
            mavenBom("org.testcontainers:testcontainers-bom:1.19.8")
        }
    }

    dependencies {
        // package-info.java 의 ApplicationModule 애노테이션 컴파일용
        "compileOnly"("org.springframework.modulith:spring-modulith-api")
        // 모든 모듈이 JUnit 5 기반 테스트를 바로 시작할 수 있게 공통 제공
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        // Gradle 테스트 태스크를 JUnit Platform 으로 통일
        useJUnitPlatform()
    }
}
