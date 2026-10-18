plugins {
    id("libreauto.android.library")
    id("libreauto.android.compose")
}

android {
    namespace = "io.github.octoclonius.libreauto.car"
}

dependencies {
    implementation(project(":session"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.coroutines.android)
}
