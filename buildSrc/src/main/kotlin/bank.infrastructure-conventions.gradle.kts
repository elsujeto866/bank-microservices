// =============================================================================
// Infrastructure module conventions — the outermost ring.
//
// This is where the dirt lives, and that is by design: HTTP controllers, JPA
// entities, Kafka producers and consumers, Spring configuration, the main
// class. Everything the business rules must never know about.
//
// Frameworks are unrestricted here. That is the whole point of pushing them out
// to the edge.
// =============================================================================

plugins {
    id("bank.java-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(platform(libs.spring.boot.bom))

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ArchUnit runs the architecture rules the Gradle module graph cannot see:
    // package naming, annotation placement, "no cycles", and so on.
    testImplementation(libs.archunit.junit5)
}
