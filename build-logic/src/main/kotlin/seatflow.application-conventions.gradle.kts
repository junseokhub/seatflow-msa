import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("seatflow.java-base-conventions")
    id("org.springframework.boot")
}

tasks.withType<BootJar> {
    layered {
        enabled = true
    }
}