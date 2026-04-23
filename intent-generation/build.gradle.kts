plugins {
    `java-library`
}

dependencies {
    api(project(":market-data"))
    api("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
