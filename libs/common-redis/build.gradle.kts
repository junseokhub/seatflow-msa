plugins {
    id("seatflow.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}