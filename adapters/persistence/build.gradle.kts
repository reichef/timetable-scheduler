plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":application"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.bcrypt)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(project(":application")))
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.logback.classic)
}
