package io.github.octoclonius.libreauto.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal const val SDK = 37

internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        compileSdk { version = release(SDK) }
        buildToolsVersion = "37.0.0"
        defaultConfig.minSdk = SDK
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions { allWarningsAsErrors.set(true) }
    }
    configureTests()
}
