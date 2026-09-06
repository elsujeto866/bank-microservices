// =============================================================================
// Base conventions applied to EVERY module in the build.
//
// Everything here is a rule we never want to restate: the Java version, the
// encoding, how tests run, how warnings are treated. Written once, enforced
// everywhere.
// =============================================================================

plugins {
    // `java-library`, not plain `java`. It adds the `api` configuration, which
    // is how a module declares "this dependency is part of my public signature,
    // so my consumers must see it too" — as opposed to `implementation`, which
    // is an internal detail and stays off the consumer's compile classpath.
    `java-library`
}

java {
    toolchain {
        // A toolchain, not `sourceCompatibility`. Gradle locates (or downloads)
        // a real JDK 21 instead of trusting whatever JAVA_HOME happens to be.
        // The build produces identical bytecode on every machine and in CI.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing", // annotation processors (Lombok) are expected
            "-parameters",        // keeps parameter names at runtime; Jackson and Spring need them
        )
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

// -----------------------------------------------------------------------------
// Lombok
//
// Declared `compileOnly` + `annotationProcessor` on purpose. Lombok runs during
// compilation and writes plain bytecode; it must NOT appear on the runtime
// classpath. This is why a domain module can use @Value or @Getter and still be
// a zero-dependency artifact at runtime.
// -----------------------------------------------------------------------------
val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}
