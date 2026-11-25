package io.github.octoclonius.libreauto.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.LockMode

internal fun Project.configureDependencyLocking() {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(
            providers
                .systemProperty("idea.sync.active")
                .map { if (it.toBoolean()) LockMode.DEFAULT else LockMode.STRICT }
                .orElse(LockMode.STRICT)
        )
    }
    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("Resolves every configuration at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "$path must be run with --write-locks"
            }
        }
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { it.incoming.resolutionResult.root }
        }
    }
}
