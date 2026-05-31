plugins {
    `java-library`
}

dependencies {
    api(project(":execution"))
    api("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
