plugins {
    id("bank.infrastructure-conventions")
}

dependencies {
    implementation(project(":services:account-service:application"))
}
