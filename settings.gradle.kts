rootProject.name = "seatflow-msa"
includeBuild("build-logic")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include(
    "libs:common-web",
    "libs:common-events",
    "libs:common-kafka",
    "libs:common-redis",
    "libs:common-jwt",
    "libs:common-outbox-jpa",
    "services:auth-service",
    "services:user-service",
    "services:reservation-service",
    "services:seat-service",
    "services:payment-service",
    "services:show-service",
)