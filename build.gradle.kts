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
