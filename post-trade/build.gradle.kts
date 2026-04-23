plugins {
    `java-library`
}

dependencies {
    api(project(":execution"))
    api(project(":market-data"))
    api("org.springframework:spring-context")
}
