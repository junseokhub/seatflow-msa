plugins {
    id("seatflow.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("io.micrometer:micrometer-registry-prometheus")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-security")
}