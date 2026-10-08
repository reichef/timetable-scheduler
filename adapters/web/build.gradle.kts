plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":application"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.call.id)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(project(":application")))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.logback.classic)
}
