buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

plugins {
    `kotlin-dsl`
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

dependencies {
    compileOnly(libs.aboutlibraries.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.wire.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("aboutLibraries") {
            id = "libreauto.aboutlibraries"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AboutLibrariesConventionPlugin"
        }
        register("androidApplication") {
            id = "libreauto.android.application"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "libreauto.android.library"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "libreauto.jvm.library"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidAidl") {
            id = "libreauto.android.aidl"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AndroidAidlConventionPlugin"
        }
        register("androidProfiling") {
            id = "libreauto.android.profiling"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AndroidProfilingConventionPlugin"
        }
        register("androidCompose") {
            id = "libreauto.android.compose"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.AndroidComposeConventionPlugin"
        }
        register("spotless") {
            id = "libreauto.spotless"
            implementationClass =
                "io.github.octoclonius.libreauto.buildlogic.SpotlessConventionPlugin"
        }
        register("wire") {
            id = "libreauto.wire"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.WireConventionPlugin"
        }
    }
}
