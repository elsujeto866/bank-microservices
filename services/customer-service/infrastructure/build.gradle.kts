plugins {
    id("bank.openapi-conventions")
}

dependencies {
    implementation(project(":services:customer-service:application"))

    // Required by the code generated from the contract.
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

openApiGenerate {
    inputSpec.set("$rootDir/contracts/customer-service/openapi.yaml")
    apiPackage.set("com.bank.customer.infrastructure.rest.generated.api")
    modelPackage.set("com.bank.customer.infrastructure.rest.generated.model")
}
