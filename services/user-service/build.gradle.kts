plugins {
	id("seatflow.application-conventions")
}

dependencies {
	implementation(project(":libs:common-web"))
	implementation(project(":libs:common-kafka"))
	implementation(project(":libs:common-jwt"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	runtimeOnly("com.mysql:mysql-connector-j")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
}