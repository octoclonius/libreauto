plugins {
    id("libreauto.android.library")
    id("libreauto.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.octoclonius.libreauto.phone"
}

dependencies {
    implementation(project(":projection"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
