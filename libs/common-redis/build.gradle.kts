plugins {
    java
    `java-library`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}