// Project rules for the layout examples of Constitution I, run by ktlint from the root build (`layoutCheck`).
// Build tooling only: no application module depends on this module.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(libs.ktlint.rule.engine.core)
    compileOnly(libs.ktlint.cli.ruleset.core)

    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.test)
    testImplementation(libs.ktlint.ruleset.standard)
    testRuntimeOnly(libs.logback.classic)
}
