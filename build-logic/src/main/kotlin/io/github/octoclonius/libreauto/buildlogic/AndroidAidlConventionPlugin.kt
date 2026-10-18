package io.github.octoclonius.libreauto.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidAidlConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.getByType(CommonExtension::class.java).buildFeatures.aidl = true
    }
}
