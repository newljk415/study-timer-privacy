plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 built-in Kotlin: this only pins the Kotlin Gradle Plugin version on the classpath.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
