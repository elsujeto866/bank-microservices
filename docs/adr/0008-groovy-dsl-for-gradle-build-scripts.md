# 8. Groovy DSL for Gradle build scripts

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** the implicit Kotlin DSL choice made in ADR-0002

## Context

Gradle offers two languages for build scripts:

- **Groovy DSL** (`build.gradle`) — dynamically typed, the original, and still
  what the overwhelming majority of Java build scripts, Stack Overflow answers
  and internal documentation are written in.
- **Kotlin DSL** (`build.gradle.kts`) — statically typed, the default for new
  projects since Gradle 8.2, with real IDE completion and compile-time errors.

On technical merit alone the Kotlin DSL is the stronger option for this build,
and it is what the project started with. A typo in a Groovy script is a runtime
failure discovered halfway through a build; in Kotlin it is a red squiggle
before the file is saved. That difference is not theoretical — while setting up
this project, the Kotlin compiler caught a missing `java-library` plugin as
`Unresolved reference 'api'` at configuration time.

But a build script is not read by the compiler alone. It is read, extended and
debugged by whoever maintains the service, usually under time pressure and
usually by copying a pattern from somewhere else in the organisation.

The target environment for this codebase — and the environment its author works
in — standardises on the Groovy DSL.

## Decision

Use the **Groovy DSL** for every build script: the settings file, the root
build, the module builds and the `buildSrc` convention plugins.

Convention plugins move from `buildSrc/src/main/kotlin/*.gradle.kts` to
`buildSrc/src/main/groovy/*.gradle`, applied through the
`groovy-gradle-plugin`. Everything else about the build is unchanged: the
module graph, the fitness functions, the version catalog, the toolchain and the
checksum-pinned wrapper all behave identically.

One mechanical difference is worth recording, because it is the part that is
easy to get wrong. Precompiled script plugins do not receive the generated
type-safe `libs.` accessors in either language. The Kotlin DSL could work around
this by putting the generated accessor jar on the `buildSrc` classpath. In
Groovy the supported route is to ask for the catalog through its extension:

```groovy
import org.gradle.api.artifacts.VersionCatalogsExtension

def libs = extensions.getByType(VersionCatalogsExtension).named('libs')

dependencies {
    compileOnly libs.findLibrary('lombok').get()
}
```

More verbose, and the alias is a string rather than a checked accessor — but it
removes the classpath hack entirely, and `gradle/libs.versions.toml` remains
the single place versions are declared.

## Consequences

- The build reads like every other Java build in the target organisation.
  Somebody joining this project can apply what they already know, and can
  apply what they learn here everywhere else.
- The vast majority of Gradle answers, plugin documentation and internal
  examples are directly usable without translation.
- Cost: **the safety net is gone.** A misspelled property in a Groovy script
  fails at configuration time with a message about a missing method, if it
  fails at all — some typos silently do nothing. The mitigation is that almost
  all logic lives in five convention plugins that every build exercises, so a
  broken one fails immediately rather than lurking in a rarely built module.
- Cost: no IDE completion inside the convention plugins, and catalog aliases
  are unchecked strings. A renamed alias in the TOML fails at build time, not
  at edit time.
- Faster configuration on a cold build — Groovy scripts are not compiled by the
  Kotlin compiler first.

## Alternatives considered

- **Keep the Kotlin DSL.** Technically better, and the direction Gradle itself
  is heading. Rejected: the value of matching the environment the code will
  actually live and be reviewed in outweighs the type safety, and picking the
  unfamiliar option would mean learning two new things at once instead of the
  one that matters.
- **Mix the two** — Groovy for module scripts, Kotlin for `buildSrc`. Rejected:
  two languages in one build is the worst of both. Anyone editing the build has
  to know which file follows which set of rules.
