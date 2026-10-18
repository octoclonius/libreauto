plugins {
    id("libreauto.android.library")
}

android {
    namespace = "io.github.octoclonius.libreauto.privileged"
}

dependencies {
    api(project(":privileged-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
