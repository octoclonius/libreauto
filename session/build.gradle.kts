plugins {
    id("libreauto.android.library")
}

android {
    namespace = "io.github.octoclonius.libreauto.session"
}

dependencies {
    implementation(project(":protocol"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
