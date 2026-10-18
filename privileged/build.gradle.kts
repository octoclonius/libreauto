plugins {
    id("libreauto.android.library")
}

android {
    namespace = "io.github.octoclonius.libreauto.privileged"
}

dependencies {
    api(project(":privileged-api"))
}
