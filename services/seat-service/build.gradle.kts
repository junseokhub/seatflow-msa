plugins {
	id("seatflow.application-conventions")
}

dependencies {
	implementation(project(":libs:common-web"))
	implementation(project(":libs:common-events"))
	implementation(project(":libs:common-outbox-jpa"))
	implementation(project(":libs:common-kafka"))
	implementation(project(":libs:common-jwt"))
	implementation(project(":libs:common-redis"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.kafka:spring-kafka-test")
}