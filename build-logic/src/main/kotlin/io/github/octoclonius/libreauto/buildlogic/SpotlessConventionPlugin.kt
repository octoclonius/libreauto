package io.github.octoclonius.libreauto.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")
            val ktfmt =
                extensions
                    .getByType<VersionCatalogsExtension>()
                    .named("libs")
                    .findVersion("ktfmt")
                    .get()
                    .requiredVersion
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("*/src/**/*.kt")
                    ktfmt(ktfmt).kotlinlangStyle()
                }
                kotlinGradle {
                    target("*.gradle.kts", "*/*.gradle.kts")
                    ktfmt(ktfmt).kotlinlangStyle()
                }
            }
        }
    }
}
