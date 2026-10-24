package io.github.octoclonius.libreauto.buildlogic

import com.squareup.wire.gradle.WireExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

class WireConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.squareup.wire")
            extensions.configure<WireExtension> { kotlin {} }
            tasks.withType<KotlinCompilationTask<*>>().configureEach {
                compilerOptions.freeCompilerArgs.add(
                    "-Xwarning-level=UNNECESSARY_NOT_NULL_ASSERTION:disabled"
                )
            }
        }
    }
}
