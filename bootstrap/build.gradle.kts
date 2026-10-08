plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapters:persistence"))
    implementation(project(":adapters:web"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("timetablescheduler.bootstrap.MainKt")
}
