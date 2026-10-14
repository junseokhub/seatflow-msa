plugins {
    id("seatflow.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("io.micrometer:micrometer-registry-prometheus")
    // Sleuth 후속. traceId/spanId를 만들어 MDC에 넣고 로그 패턴에 자동으로 붙인다(actuator 필요).
    // 구현체는 벤더 중립 표준인 OpenTelemetry, 전파 헤더는 W3C traceparent.
    api("io.micrometer:micrometer-tracing-bridge-otel")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
}