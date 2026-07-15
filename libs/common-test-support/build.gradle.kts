plugins {
    id("seatflow.library-conventions")
    `java-test-fixtures`
}

dependencies {
    // 버전을 명시하지 않는다. spring-boot-testcontainers가 Spring Boot의 dependency
    // management(BOM)를 통해 Testcontainers 전체 버전(1.21.4, Spring Boot 3.5.13
    // 기준)을 이미 관리해주고 있다. 여기서 버전을 직접 못박으면(예: 1.21.3) BOM이
    // 원하는 버전과 충돌해 일부 모듈만 다른 버전으로 끌려가는 문제가 생긴다
    // (jdbc:1.21.3 -> 1.21.4로 강제 승격되면서 testcontainers 코어와 미묘하게
    // 어긋났던 것도 이 충돌의 결과였다).
    testFixturesApi("org.springframework.boot:spring-boot-starter-test")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")   // @ServiceConnection, BOM 적용
    testFixturesApi("org.testcontainers:testcontainers")
    testFixturesApi("org.testcontainers:jdbc")
    testFixturesApi("org.testcontainers:junit-jupiter")
    testFixturesApi("org.testcontainers:mysql")
    testFixturesApi("org.testcontainers:kafka")
    testFixturesApi("org.testcontainers:mongodb")
    testFixturesApi("junit:junit")
    testFixturesApi("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.2.2")
}