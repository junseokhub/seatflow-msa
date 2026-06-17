plugins {
    id("seatflow.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(project(":libs:common-kafka"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}