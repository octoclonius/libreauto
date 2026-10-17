package io.github.octoclonius.libreauto.buildlogic

import com.squareup.wire.gradle.WireExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class WireConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.squareup.wire")
            extensions.configure<WireExtension> { kotlin {} }
        }
    }
}
