plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":application"))
    implementation(libs.timefold.solver.core)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(project(":application")))
    testRuntimeOnly(libs.logback.classic)
}

// The SC-001 performance test runs several minutes; it is run on demand with `performanceTest` (G12).
tasks.test {
    useJUnitPlatform { excludeTags("performance") }
}

val performanceTest by tasks.registering(Test::class) {
    description = "Runs the tagged scheduling performance tests (SC-001)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("performance") }
    outputs.upToDateWhen { false }
}
