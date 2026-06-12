rootProject.name = "seatflow-msa"

include(
    "libs:common-web",
    "libs:common-events",
    "services:auth-service",
    "services:user-service",
    "services:reservation-service",
    "services:seat-service",
    "services:payment-service",
    "services:show-service",
)