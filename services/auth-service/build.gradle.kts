plugins {
	id("seatflow.application-conventions")
}

dependencies {
	implementation(project(":libs:common-web"))
	implementation(project(":libs:common-kafka"))
	implementation(project(":libs:common-jwt"))
	implementation(project(":libs:common-redis"))
	implementation(project(":libs:common-outbox-jpa"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")

	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	runtimeOnly("com.mysql:mysql-connector-j")

	"testImplementation"(testFixtures(project(":libs:common-test-support")))
	testImplementation("org.springframework.security:spring-security-test")
}