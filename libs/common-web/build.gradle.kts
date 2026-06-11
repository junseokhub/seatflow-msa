plugins {
    id("io.spring.dependency-management")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}

tasks.withType<Jar> {
    enabled = true
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
}