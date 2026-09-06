plugins {
    id("bank.application-conventions")
}

dependencies {
    // `api`, not `implementation`: domain types (Customer, Money, AccountId)
    // appear in the public signatures of the use cases, so callers must see them.
    api(project(":services:customer-service:domain"))
}
