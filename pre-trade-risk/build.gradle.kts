plugins {
    `java-library`
}

dependencies {
    api(project(":market-data"))
    api(project(":intent-generation"))
    api("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
