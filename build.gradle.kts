import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "de.reichef"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
        dependencies {
            "testImplementation"(kotlin("test"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

// Formatting standard of Constitution I (spec 003, contracts/layout-check.md): ktlint with all non-experimental
// standard rules plus the project rules of tooling/layout-rules. `-PlayoutPath=<directory>` limits the run to one
// module.
val ktlint: Configuration =
    configurations.create("ktlint") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        }
    }

/** The jar of the project rules, passed to ktlint as additional rule set. */
val layoutRules: Configuration =
    configurations.create("layoutRules") {
        isTransitive = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
    }

dependencies {
    ktlint(libs.ktlint.cli)
    layoutRules(project(":tooling:layout-rules"))
}

/** One line of ktlint's plain report: `<path>:<line>:<column>: <message> (<rule id>)`. */
val layoutViolationLine = Regex("""^\S.*:\d+:\d+: .+ \([a-z-]+:[a-z-]+\)$""")

fun registerLayoutTask(
    taskName: String,
    taskDescription: String,
    formatFirst: Boolean,
) = tasks.register<JavaExec>(taskName) {
    group = "verification"
    description = taskDescription
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir

    val pathPrefix =
        providers
            .gradleProperty("layoutPath")
            .orNull
            ?.trimEnd('/')
            ?.let { "$it/" }
            .orEmpty()
    val report = java.io.ByteArrayOutputStream()

    args(
        listOfNotNull(
            "--relative",
            "--reporter=plain",
            "--format".takeIf { formatFirst },
            "$pathPrefix**/*.kt",
            "$pathPrefix**/*.kts",
            "!**/build/**",
            "!.claude/**",
        ),
    )
    inputs.files(layoutRules)
    argumentProviders.add(CommandLineArgumentProvider { layoutRules.files.map { "--ruleset=${it.absolutePath}" } })
    isIgnoreExitValue = true
    standardOutput = report

    // ktlint ends with exit value 1 when it found violations. Exit value 1 without any reported violation, or any
    // other non-zero value, means ktlint itself could not run.
    doLast {
        val exitValue = executionResult.get().exitValue
        val violations = report.toString().lines().filter { layoutViolationLine.matches(it) }

        if (exitValue !in 0..1 || (exitValue == 1 && violations.isEmpty())) {
            throw GradleException("ktlint could not run (exit value $exitValue)")
        }
        val fileCount = violations.map { it.substringBefore(':') }.distinct().size

        violations.forEach(::println)

        if (violations.isEmpty()) {
            println("Layout check: no violations")
        } else {
            println("Layout check: ${violations.size} violation(s) in $fileCount file(s)")
            throw GradleException("Layout check failed: ${violations.size} violation(s); see the report above")
        }
    }
}

registerLayoutTask(
    taskName = "layoutCheck",
    taskDescription = "Checks the Kotlin code against ktlint's standard rules and the project layout rules.",
    formatFirst = false,
)
registerLayoutTask(
    taskName = "layoutFormat",
    taskDescription = "Applies all auto-fixes of the layout check, then reports the remaining violations.",
    formatFirst = true,
)
