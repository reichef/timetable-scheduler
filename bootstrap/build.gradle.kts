plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapters:persistence"))
    implementation(project(":adapters:web"))
    implementation(project(":adapters:solver"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.logback.classic)
    testImplementation(testFixtures(project(":application")))
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("timetablescheduler.bootstrap.MainKt")
}

// The test school (specs/002-timetable-generation/test-set-bw-gymnasium.md) is test data only: its scenario runs a
// full generation (about 10 minutes) and is started on demand, never as part of `build`.
tasks.test {
    useJUnitPlatform { excludeTags("testschool") }
}

val testSchoolTest by tasks.registering(Test::class) {
    description = "Seeds the fictitious test school into a PostgreSQL container and runs one generation (feature 002, T114)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("testschool") }
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}

val seedTestSchool by tasks.registering(JavaExec::class) {
    description =
        "Seeds the fictitious test school (-PtestSchool=full|understaffed) into the database of the TTS_* variables (must be empty)."
    group = "application"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("timetablescheduler.bootstrap.testschool.SeedTestSchoolKt")
    // `-PtestSchool=understaffed` seeds the teacher-shortage variant instead of the full test school.
    args(providers.gradleProperty("testSchool").getOrElse("full"))
}
