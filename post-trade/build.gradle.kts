plugins {
    `java-library`
}

dependencies {
    api(project(":execution"))
    api(project(":market-data"))
    api("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
