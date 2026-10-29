plugins {
    id("libreauto.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.octoclonius.libreauto.projection"
}

dependencies {
    implementation(project(":privileged-api"))
    implementation(project(":protocol"))
    api(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
