package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.calls.Caller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal data class CallScreenUiState(
    val call: CallUiState,
    val heldCaller: Caller?,
    val buttons: CallScreenButtons,
)

internal class CallScreenState(
    private val repository: CallRepository,
    private val uiStates: CallUiStates,
) {

    private val isOpen = MutableStateFlow(false)

    private val _screen = MutableStateFlow<CallScreenUiState?>(null)
    val screen: StateFlow<CallScreenUiState?> = _screen.asStateFlow()

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    fun start(scope: CoroutineScope) {
        val shown = repository.calls.map(::callScreenCalls).distinctUntilChanged()
        val primary =
            uiStates.of(shown.map { it?.primary }.distinctUntilChanged(), CALL_SCREEN_AVATAR_SIZE)
        scope.launch {
            combine(primary, shown, repository.audio) { call, calls, audio ->
                    Triple(call, calls, audio.isMuted)
                }
                .filter { (call, calls, _) -> call?.call?.id == calls?.primary?.id }
                .collect { (call, calls, isMuted) ->
                    _screen.value =
                        if (call == null || calls == null) {
                            null
                        } else {
                            CallScreenUiState(
                                call,
                                calls.secondary?.caller,
                                callScreenButtons(calls, isMuted),
                            )
                        }
                }
        }
        scope.launch { shown.collect { if (it == null) isOpen.value = false } }
        scope.launch {
            combine(isOpen, _screen) { open, screen -> open && screen != null }
                .collect { _isShown.value = it }
        }
    }

    fun open() {
        if (callScreenCalls(repository.calls.value) != null) isOpen.value = true
    }

    fun close(): Boolean = isOpen.getAndUpdate { false }

    fun perform(action: CallAction) {
        when (action) {
            is CallAction.SetMuted -> repository.setMuted(action.isMuted)
            is CallAction.Hold -> repository.hold(action.id)
            is CallAction.Unhold -> repository.unhold(action.id)
            is CallAction.Merge -> repository.merge(action.id)
            is CallAction.SwapConference -> repository.swapConference(action.id)
            is CallAction.Disconnect -> repository.disconnect(action.id)
        }
    }
}
