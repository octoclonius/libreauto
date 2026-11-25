package io.github.octoclonius.libreauto.buildlogic

import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import com.mikepenz.aboutlibraries.plugin.StrictMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AboutLibrariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.mikepenz.aboutlibraries.plugin.android")
            extensions.configure<AboutLibrariesExtension> {
                offlineMode.set(true)
                collect { configPath.set(file("config")) }
                license {
                    strictMode.set(StrictMode.FAIL)
                    allowedLicenses.add("Apache-2.0")
                    allowedLicensesMap.put("MIT", listOf("dev.rikka.shizuku"))
                }
            }
        }
    }
}
