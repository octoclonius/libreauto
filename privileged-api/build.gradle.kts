plugins {
    id("libreauto.android.library")
    id("libreauto.android.aidl")
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.github.octoclonius.libreauto.privileged.api"
}
