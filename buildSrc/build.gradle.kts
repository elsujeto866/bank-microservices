plugins {
    // Lets us write convention plugins as .gradle.kts files with full type safety.
    `kotlin-dsl`
}

dependencies {
    // Plugins we want to apply from inside our own convention plugins must be
    // on buildSrc's compile classpath first.
    implementation(libs.springBoot.gradlePlugin)

    // Gradle generates type-safe catalog accessors for build scripts, but does
    // NOT expose them to precompiled script plugins. Putting the generated
    // accessor jar on the classpath is the documented workaround, and it lets
    // the convention plugins below read `libs` exactly like a normal script.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
