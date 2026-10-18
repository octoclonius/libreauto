plugins {
    id("libreauto.android.application")
}

android {
    namespace = "io.github.octoclonius.libreauto"

    defaultConfig {
        applicationId = "io.github.octoclonius.libreauto"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
}

dependencies {
    implementation(project(":car"))
    implementation(project(":phone"))
    implementation(project(":privileged"))
    implementation(project(":session"))
}