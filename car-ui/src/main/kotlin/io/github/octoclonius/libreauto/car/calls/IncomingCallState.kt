package io.github.octoclonius.libreauto.car.calls

import android.os.SystemClock
import io.github.octoclonius.libreauto.car.overlay.isTouchAccepted
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class IncomingCallState(
    private val repository: CallRepository,
    private val uiStates: CallUiStates,
) {

    private val hiddenIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _call = MutableStateFlow<PhoneCall?>(null)
    val call: StateFlow<PhoneCall?> = _call.asStateFlow()
    private var shownAt = 0L

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    private val _incoming = MutableStateFlow<CallUiState?>(null)
    val incoming: StateFlow<CallUiState?> = _incoming.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.calls, hiddenIds, ::incomingCall).collect { next ->
                if (next != null && next.id != _call.value?.id) {
                    shownAt = SystemClock.elapsedRealtime()
                }
                _call.value = next
                _isShown.value = next != null
            }
        }
        scope.launch { uiStates.of(call, CALLER_AVATAR_SIZE).collect { _incoming.value = it } }
    }

    fun answer(id: Int) {
        if (isTouchAccepted(shownAt, SystemClock.elapsedRealtime())) repository.answer(id)
    }

    fun decline(id: Int) {
        if (isTouchAccepted(shownAt, SystemClock.elapsedRealtime())) repository.reject(id)
    }

    fun hide() {
        val shown = call.value ?: return
        hiddenIds.update { it + shown.id }
    }
}
