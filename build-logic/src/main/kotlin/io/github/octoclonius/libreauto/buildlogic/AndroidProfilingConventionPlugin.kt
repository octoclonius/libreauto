package io.github.octoclonius.libreauto.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal const val BENCHMARK = "benchmark"

class AndroidProfilingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val frameTap =
                providers
                    .gradleProperty("libreauto.frameTap")
                    .map(String::toBoolean)
                    .orElse(false)
                    .get()
            val commit =
                providers
                    .exec {
                        commandLine("git", "rev-parse", "HEAD")
                        isIgnoreExitValue = true
                    }
                    .standardOutput
                    .asText
                    .get()
                    .trim()
            val isDirty =
                providers
                    .exec {
                        commandLine("git", "status", "--porcelain", "--untracked-files=no")
                        isIgnoreExitValue = true
                    }
                    .standardOutput
                    .asText
                    .get()
                    .isNotBlank()
            extensions.configure<LibraryExtension> {
                buildFeatures.buildConfig = true
                buildTypes {
                    create(BENCHMARK) {
                        initWith(getByName("release"))
                        matchingFallbacks += "release"
                    }
                    for (name in listOf("debug", BENCHMARK)) {
                        getByName(name) {
                            buildConfigField("boolean", "FRAME_TAP", frameTap.toString())
                            buildConfigField("String", "GIT_COMMIT", "\"$commit\"")
                            buildConfigField("boolean", "GIT_DIRTY", isDirty.toString())
                        }
                    }
                }
                sourceSets {
                    for (name in listOf("debug", BENCHMARK)) {
                        getByName(name).kotlin.directories += "src/profiling/kotlin"
                    }
                }
            }
        }
    }
}
