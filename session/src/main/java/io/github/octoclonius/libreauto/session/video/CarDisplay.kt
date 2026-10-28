package io.github.octoclonius.libreauto.session.video

import android.app.PendingIntent
import android.graphics.Insets
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CarDisplayState(val id: Int, val carDensity: Int)

class CarDisplay
internal constructor(
    private val privileged: PrivilegedServiceConnection,
    internal val home: CarHome,
) {
    private val _state = MutableStateFlow<CarDisplayState?>(null)
    val state: StateFlow<CarDisplayState?> = _state.asStateFlow()

    private val _carDensity = MutableStateFlow<Int?>(null)
    val carDensity: StateFlow<Int?> = _carDensity.asStateFlow()

    fun showHome() {
        val id = state.value?.id ?: return
        privileged.call("show the home") { it.showActivity(id, home.intent) }
    }

    fun show(activity: PendingIntent) {
        val id = state.value?.id ?: return
        privileged.call("show an activity") { it.showActivity(id, activity) }
    }

    fun sendKey(keyCode: Int) {
        val id = state.value?.id ?: return
        privileged.call("take key $keyCode") { it.sendKey(id, keyCode) }
    }

    internal fun onCarDensity(carDensity: Int?) {
        _carDensity.value = carDensity
    }

    internal fun onCreated(id: Int, carDensity: Int) {
        _state.value = CarDisplayState(id, carDensity)
    }

    internal fun onReleased(id: Int) {
        _state.update { if (it?.id == id) null else it }
    }
}

internal fun CarInsets.toInsets(): Insets = Insets.of(left, top, right, bottom)
