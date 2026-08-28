plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Shared domain interfaces so SettingsRepositoryFake can implement ISettingsRepository.
    implementation(project(":core:domain"))

    // Exposed on consumers' test classpaths via testImplementation(project(":core:testing")).
    api(libs.junit)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.test)
}
