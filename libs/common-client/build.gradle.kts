plugins {
    id("seatflow.library-conventions")
    id("io.spring.dependency-management") version "1.1.7"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

dependencyManagement {
    imports {
        // Spring Boot 3.5.x와 호환되는 Spring Cloud 버전.
        // Spring Cloud 자체는 Boot처럼 자동으로 버전이 관리되지 않아서,
        // 이 BOM을 명시적으로 임포트해야 spring-cloud-starter-* 의 버전 공백이 채워진다.
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

dependencies {
    api(project(":libs:common-web"))

    api("org.springframework.cloud:spring-cloud-starter-openfeign")

    // 서킷 브레이커. Feign은 spring-cloud-starter-circuitbreaker-resilience4j를 통해
    // circuitBreakerFactory를 자동으로 연동한다(spring.cloud.openfeign.circuitbreaker.enabled).
    api("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
}