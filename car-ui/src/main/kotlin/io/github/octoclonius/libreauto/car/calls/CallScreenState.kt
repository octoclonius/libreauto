package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallAudio
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
        val keypad: CallScreenKeypad?,
        val audioOutputs: CallScreenAudioOutputs?,
    ) : CallScreenUiState

    data class Ringing(override val call: CallUiState, val canAnswerAndRelease: Boolean) :
        CallScreenUiState
}

internal class CallScreenState(
    private val repository: CallRepository,
    private val uiStates: CallUiStates,
    private val popUpCall: StateFlow<PhoneCall?>,
) {

    private val stack = MutableStateFlow<List<CallScreenEntry>>(emptyList())
    private val keypad = MutableStateFlow(Keypad())
    private val isAudioOutputsShown = MutableStateFlow(false)
    private var toneCallId: Int? = null

    private val _surfaces = MutableStateFlow(CallScreenSurfaces())
    val surfaces: StateFlow<CallScreenSurfaces> = _surfaces.asStateFlow()

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    private val _ongoingScreen = MutableStateFlow<CallScreenUiState?>(null)
    val ongoingScreen: StateFlow<CallScreenUiState?> = _ongoingScreen.asStateFlow()

    private val _ringingScreen = MutableStateFlow<CallScreenUiState?>(null)
    val ringingScreen: StateFlow<CallScreenUiState?> = _ringingScreen.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous = emptyList<PhoneCall>()
            repository.calls.collect { calls ->
                stack.update { reconciledCallScreen(it, calls) }
                keypad.update { reconciledKeypad(it, previous, calls) }
                isAudioOutputsShown.update { reconciledAudioOutputsShown(it, previous, calls) }
                previous = calls
                updateSurfaces()
            }
        }
        scope.launch { popUpCall.collect { updateSurfaces() } }
        val ongoingView =
            combine(repository.calls, keypad, isAudioOutputsShown) { calls, keypad, isShown ->
                callScreenView(CallScreenEntry.InCall, calls, keypad, isShown)
            }
        val ringingCallId = surfaces.map { it.ringingCallId }.distinctUntilChanged()
        val ringingView =
            combine(ringingCallId, repository.calls, keypad) { id, calls, keypad ->
                id?.let {
                    callScreenView(
                        CallScreenEntry.Ringing(it),
                        calls,
                        keypad,
                        isAudioOutputsShown = false,
                    )
                }
            }
        collectScreen(scope, ongoingView, _ongoingScreen)
        collectScreen(scope, ringingView, _ringingScreen)
    }

    fun open(entry: CallScreenEntry) {
        stack.update { pushedCallScreen(it, entry, repository.calls.value) }
        updateSurfaces()
    }

    fun back(): Boolean {
        val entries = stack.value
        if (entries.isEmpty()) return false
        val view =
            callScreenView(
                entries.last(),
                repository.calls.value,
                keypad.value,
                isAudioOutputsShown.value,
            )
        if (view is CallScreenView.InCall && view.isAudioOutputsShown) {
            isAudioOutputsShown.value = false
            return true
        }
        if (view is CallScreenView.InCall && view.keypad != null) {
            keypad.update { it.copy(isShown = false) }
            return true
        }
        stack.value = entries.dropLast(1)
        updateSurfaces()
        return true
    }

    fun close(): Boolean {
        val wasOpen = stack.getAndUpdate { emptyList() }.isNotEmpty()
        updateSurfaces()
        return wasOpen
    }

    fun perform(action: CallAction) {
        when (action) {
            is CallAction.SetMuted -> repository.setMuted(action.isMuted)
            is CallAction.SetKeypadShown -> keypad.update { it.copy(isShown = action.isShown) }
            is CallAction.PlayDtmfTone -> {
                keypad.update { it.copy(digits = it.digits + action.digit) }
                toneCallId = action.id
                repository.playDtmfTone(action.id, action.digit)
            }
            CallAction.StopDtmfTone -> {
                toneCallId?.let(repository::stopDtmfTone)
                toneCallId = null
            }
            is CallAction.SetAudioOutputsShown -> isAudioOutputsShown.value = action.isShown
            is CallAction.SelectAudioOutput -> {
                isAudioOutputsShown.value = false
                repository.requestAudioOutput(action.output)
            }
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

    private fun updateSurfaces() {
        val calls = repository.calls.value
        val top = reconciledCallScreen(stack.value, calls).lastOrNull()
        val next = callScreenSurfaces(top, popUpCall.value, calls)
        _surfaces.value = next
        _isShown.value = next.shown != null
    }

    private fun collectScreen(
        scope: CoroutineScope,
        view: Flow<CallScreenView?>,
        screen: MutableStateFlow<CallScreenUiState?>,
    ) {
        val distinctView = view.distinctUntilChanged()
        val shownCall =
            uiStates.of(
                distinctView.map { it?.shownCall }.distinctUntilChanged(),
                CALL_SCREEN_AVATAR_SIZE,
            )
        scope.launch {
            combine(shownCall, distinctView, repository.audio, ::Triple)
                .filter { (call, shown, _) -> call?.call?.id == shown?.shownCall?.id }
                .collect { (call, shown, audio) ->
                    uiState(call, shown, audio)?.let { screen.value = it }
                }
        }
    }

    private fun uiState(
        call: CallUiState?,
        shown: CallScreenView?,
        audio: CallAudio,
    ): CallScreenUiState? {
        if (call == null || shown == null) return null
        return when (shown) {
            is CallScreenView.InCall ->
                CallScreenUiState.InCall(
                    call,
                    shown.calls.secondary?.caller,
                    callScreenButtons(shown.calls, audio, isKeypadShown = shown.keypad != null),
                    shown.keypad,
                    if (shown.isAudioOutputsShown) {
                        CallScreenAudioOutputs(audioOutputsInOrder(audio.outputs), audio.output)
                    } else {
                        null
                    },
                )
            is CallScreenView.Ringing -> CallScreenUiState.Ringing(call, shown.canAnswerAndRelease)
        }
    }
}
