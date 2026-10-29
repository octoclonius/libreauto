package io.github.octoclonius.libreauto.session.display

import android.app.PendingIntent
import android.graphics.Insets
import io.github.octoclonius.libreauto.privileged.api.ICarDisplaySession
import io.github.octoclonius.libreauto.protocol.media.CarInsets
import io.github.octoclonius.libreauto.session.privileged.call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarDisplayState(val id: Int, val carDensity: Int)

class CarDisplay internal constructor(internal val home: CarHome) {
    private val _state = MutableStateFlow<CarDisplayState?>(null)
    val state: StateFlow<CarDisplayState?> = _state.asStateFlow()

    private val _carDensity = MutableStateFlow<Int?>(null)
    val carDensity: StateFlow<Int?> = _carDensity.asStateFlow()

    @Volatile private var session: ICarDisplaySession? = null

    fun showHome() {
        call("show the home") { it.showActivity(home.intent) }
    }

    fun show(activity: PendingIntent) {
        call("show an activity") { it.showActivity(activity) }
    }

    fun sendKey(keyCode: Int) {
        call("take key $keyCode") { it.sendKey(keyCode) }
    }

    internal fun <T> call(action: String, block: (ICarDisplaySession) -> T): T? =
        session?.call(action, block)

    internal fun onCarDensity(carDensity: Int?) {
        _carDensity.value = carDensity
    }

    internal fun onCreated(session: ICarDisplaySession, id: Int, carDensity: Int) {
        this.session = session
        _state.value = CarDisplayState(id, carDensity)
    }

    internal fun onReleased(id: Int) {
        if (_state.value?.id != id) return
        session = null
        _state.value = null
    }
}

internal fun CarInsets.toInsets(): Insets = Insets.of(left, top, right, bottom)
