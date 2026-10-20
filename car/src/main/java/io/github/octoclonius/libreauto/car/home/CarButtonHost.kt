package io.github.octoclonius.libreauto.car.home

import android.content.Context
import io.github.octoclonius.libreauto.session.input.CarButtons
import io.github.octoclonius.libreauto.session.input.NavigationButton
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class CarButtonHost(context: Context) {

    private val context = context.applicationContext
    private val scope = MainScope()

    fun start() {
        scope.launch {
            CarButtons.presses.collect {
                when (it) {
                    NavigationButton.Home -> onHomeButton(context)
                    NavigationButton.Back -> onBackButton()
                }
            }
        }
    }
}
