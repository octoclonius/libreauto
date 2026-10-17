plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.wire.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "libreauto.android.application"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "libreauto.android.library"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "libreauto.jvm.library"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "libreauto.android.compose"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.AndroidComposeConventionPlugin"
        }
        register("wire") {
            id = "libreauto.wire"
            implementationClass = "io.github.octoclonius.libreauto.buildlogic.WireConventionPlugin"
        }
    }
}
