// =============================================================================
// Application module conventions — the use-case ring.
//
// Holds the use cases and the PORTS they talk through (interfaces such as
// CustomerRepository or CustomerEventPublisher). It knows the domain. It does
// not know who implements the ports.
//
// Reactor IS allowed here: Mono/Flux are part of the port signatures, so the
// use cases have to speak that language. Spring, JPA and Kafka are not.
// =============================================================================

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("bank.java-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api("io.projectreactor:reactor-core")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Same fitness function as the domain, minus Reactor. The use cases orchestrate
// business rules; they must never reach for a framework to do it.
val forbiddenGroupPrefixes = listOf(
    "org.springframework",
    "jakarta.persistence",
    "jakarta.servlet",
    "org.hibernate",
    "org.apache.kafka",
)

val runtimeArtifacts = configurations.named("runtimeClasspath")
    .flatMap { it.incoming.artifacts.resolvedArtifacts }

val verifyApplicationPurity = tasks.register("verifyApplicationPurity") {
    group = "verification"
    description = "Fails if the application module depends on a framework or an infrastructure library."

    val artifacts = runtimeArtifacts
    val forbidden = forbiddenGroupPrefixes
    val modulePath = project.path

    doLast {
        val violations = artifacts.get()
            .mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
            .filter { id -> forbidden.any { id.group.startsWith(it) } }
            .map { "${it.group}:${it.module}:${it.version}" }
            .distinct()
            .sorted()

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Architectural violation in $modulePath.")
                    appendLine("The application layer must depend on ports, not on frameworks. These leaked in:")
                    violations.forEach { appendLine("  - $it") }
                }
            )
        }
    }
}

tasks.named("check") { dependsOn(verifyApplicationPurity) }
