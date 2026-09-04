plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    // Shared test helpers (MainDispatcherRule, SettingsRepositoryFake) consumed by other modules
    // via testImplementation(project(":core:testing")).
    testImplementation(project(":core:testing"))
}
