// =============================================================================
// Root build script.
//
// Deliberately almost empty. The root project produces no artifact; it exists
// only to aggregate. Every real rule lives in buildSrc/ as a convention plugin,
// so modules opt IN to a role instead of inheriting a pile of `allprojects {}`
// configuration they never asked for.
// =============================================================================

plugins {
    base
}

// Referenced by task PATH rather than by reaching into sibling projects. Poking
// at another project's task objects at configuration time breaks the
// configuration cache and blocks Isolated Projects later on.
tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs every architectural fitness function across the build."
    dependsOn(
        ":services:customer-service:domain:verifyDomainPurity",
        ":services:customer-service:application:verifyApplicationPurity",
        ":services:account-service:domain:verifyDomainPurity",
        ":services:account-service:application:verifyApplicationPurity",
    )
}
