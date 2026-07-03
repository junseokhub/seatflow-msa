plugins {
	id("seatflow.application-conventions")
}

dependencies {
	implementation(project(":libs:common-web"))
	implementation(project(":libs:common-kafka"))
	implementation(project(":libs:common-events"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
}