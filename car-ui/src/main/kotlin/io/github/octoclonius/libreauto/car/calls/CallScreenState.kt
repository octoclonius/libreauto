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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface CallScreenUiState {
    val call: CallUiState

    data class InCall(
        override val call: CallUiState,
        val heldCaller: Caller?,
        val buttons: CallScreenButtons,
    ) : CallScreenUiState

    data class Ringing(override val call: CallUiState, val canAnswerAndRelease: Boolean) :
        CallScreenUiState
}

internal class CallScreenState(
    private val repository: CallRepository,
    private val uiStates: CallUiStates,
) {

    private val stack = MutableStateFlow<List<CallScreenEntry>>(emptyList())

    private val _screen = MutableStateFlow<CallScreenUiState?>(null)
    val screen: StateFlow<CallScreenUiState?> = _screen.asStateFlow()

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.calls.collect { calls -> stack.update { reconciledCallScreen(it, calls) } }
        }
        val view =
            combine(stack, repository.calls) { entries, calls ->
                    callScreenView(reconciledCallScreen(entries, calls).lastOrNull(), calls)
                }
                .distinctUntilChanged()
        val shownCall =
            uiStates.of(view.map { it?.shownCall }.distinctUntilChanged(), CALL_SCREEN_AVATAR_SIZE)
        scope.launch {
            combine(shownCall, view, repository.audio) { call, shown, audio ->
                    Triple(call, shown, audio.isMuted)
                }
                .filter { (call, shown, _) -> call?.call?.id == shown?.shownCall?.id }
                .collect { (call, shown, isMuted) -> _screen.value = uiState(call, shown, isMuted) }
        }
        scope.launch {
            combine(stack, _screen) { entries, screen -> entries.isNotEmpty() && screen != null }
                .collect { _isShown.value = it }
        }
    }

    fun open(entry: CallScreenEntry) {
        stack.update { pushedCallScreen(it, entry, repository.calls.value) }
    }

    fun back(): Boolean {
        val entries = stack.value
        if (entries.isEmpty()) return false
        stack.value = entries.dropLast(1)
        return true
    }

    fun close(): Boolean = stack.getAndUpdate { emptyList() }.isNotEmpty()

    fun perform(action: CallAction) {
        when (action) {
            is CallAction.SetMuted -> repository.setMuted(action.isMuted)
            is CallAction.Hold -> repository.hold(action.id)
            is CallAction.Unhold -> repository.unhold(action.id)
            is CallAction.Merge -> repository.merge(action.id)
            is CallAction.SwapConference -> repository.swapConference(action.id)
            is CallAction.Disconnect -> repository.disconnect(action.id)
            is CallAction.Answer -> repository.answer(action.id)
            is CallAction.AnswerAndRelease -> repository.answerAndRelease(action.id)
            is CallAction.Reject -> repository.reject(action.id)
        }
    }

    private fun uiState(
        call: CallUiState?,
        shown: CallScreenView?,
        isMuted: Boolean,
    ): CallScreenUiState? {
        if (call == null || shown == null) return null
        return when (shown) {
            is CallScreenView.InCall ->
                CallScreenUiState.InCall(
                    call,
                    shown.calls.secondary?.caller,
                    callScreenButtons(shown.calls, isMuted),
                )
            is CallScreenView.Ringing -> CallScreenUiState.Ringing(call, shown.canAnswerAndRelease)
        }
    }
}
