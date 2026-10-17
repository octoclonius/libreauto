package io.github.octoclonius.libreauto.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

internal val JAVA_VERSION = JavaVersion.VERSION_21

internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
