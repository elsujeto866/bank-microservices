// =============================================================================
// Settings — defines WHICH modules exist and WHERE dependencies come from.
// =============================================================================

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Modules are forbidden from declaring their own repositories. Every
    // artifact in this build resolves through the repositories listed here —
    // that is a supply-chain control, not a style preference.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bank-microservices"

// -----------------------------------------------------------------------------
// The module graph IS the architecture.
//
// Each service is split into three Gradle modules that mirror the hexagon:
//
//   domain          the business rules. Zero frameworks. Zero I/O.
//   application     the use cases and the ports they speak through.
//   infrastructure  the adapters: HTTP, JPA, Kafka, configuration.
//
// Dependencies may only point inward: infrastructure -> application -> domain.
// Because these are separate compilation units, a violation is not a code
// review comment — it is a compiler error.
// -----------------------------------------------------------------------------
include(
    ":services:customer-service:domain",
    ":services:customer-service:application",
    ":services:customer-service:infrastructure",

    ":services:account-service:domain",
    ":services:account-service:application",
    ":services:account-service:infrastructure",
)
