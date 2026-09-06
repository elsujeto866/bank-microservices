plugins {
    id("bank.infrastructure-conventions")
}

dependencies {
    implementation(project(":services:customer-service:application"))
}
