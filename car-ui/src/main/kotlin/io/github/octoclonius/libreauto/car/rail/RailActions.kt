package io.github.octoclonius.libreauto.car.rail

import io.github.octoclonius.libreauto.projection.tasks.CarTask

internal interface RailActions {

    fun onAppsButton()

    fun onAppClick(task: CarTask)

    fun onAppLongClick(task: CarTask)

    fun onAppClose(task: CarTask)

    fun onStatusClick()
}
