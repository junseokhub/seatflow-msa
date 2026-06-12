plugins {
    java
    `java-library`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}

tasks.withType<Jar> {
    enabled = true
}

dependencies {
    api("org.springframework.kafka:spring-kafka")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}