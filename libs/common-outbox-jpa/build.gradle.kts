plugins {
    id("seatflow.library-conventions")
}

dependencies {
    implementation(project(":libs:common-kafka"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}