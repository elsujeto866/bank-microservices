// =============================================================================
// Domain module conventions — the innermost ring of the hexagon.
//
// The domain holds business rules and nothing else: no Spring, no JPA, no
// Kafka, no HTTP, no JSON. It must be compilable and testable with a bare JVM.
//
// That rule is not documentation here. It is a build failure.
// =============================================================================

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("bank.java-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -----------------------------------------------------------------------------
// Architectural fitness function.
//
// Resolves this module's RUNTIME classpath and fails the build if any framework
// or infrastructure library leaked in. A developer cannot "just add" a
// @Autowired to the domain to make something work — `./gradlew check` stops it
// before the pull request is ever opened.
// -----------------------------------------------------------------------------
val forbiddenGroupPrefixes = listOf(
    "org.springframework",
    "jakarta.persistence",
    "jakarta.servlet",
    "org.hibernate",
    "org.apache.kafka",
    "com.fasterxml.jackson",
    "io.projectreactor",
)

val runtimeArtifacts = configurations.named("runtimeClasspath")
    .flatMap { it.incoming.artifacts.resolvedArtifacts }

val verifyDomainPurity = tasks.register("verifyDomainPurity") {
    group = "verification"
    description = "Fails if the domain module depends on any framework or infrastructure library."

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
                    appendLine("The domain must stay free of frameworks, but these leaked onto its runtime classpath:")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Move the offending code to the infrastructure module and talk to the domain through a port.")
                }
            )
        }
    }
}

tasks.named("check") { dependsOn(verifyDomainPurity) }
