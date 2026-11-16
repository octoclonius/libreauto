package io.github.octoclonius.libreauto.car.calls

import android.telecom.Call
import io.github.octoclonius.libreauto.projection.calls.AudioOutput
import io.github.octoclonius.libreauto.projection.calls.AudioOutputType
import io.github.octoclonius.libreauto.projection.calls.CallAudio
import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.PhoneCall

private val PRIMARY_ORDER =
    listOf(
        CallStatus.Dialing,
        CallStatus.Active,
        CallStatus.Disconnecting,
        CallStatus.Disconnected,
        CallStatus.Holding,
    )
private val SECONDARY_ORDER = listOf(CallStatus.Active, CallStatus.Holding)
private val DTMF_ORDER = listOf(CallStatus.Dialing, CallStatus.Active)
private val AUDIO_OUTPUT_ORDER =
    listOf(
        AudioOutputType.Bluetooth,
        AudioOutputType.Speaker,
        AudioOutputType.WiredHeadset,
        AudioOutputType.Earpiece,
    )

internal data class CallScreenCalls(val primary: PhoneCall, val secondary: PhoneCall?)

internal data class Keypad(val isShown: Boolean = false, val digits: String = "")

internal data class CallScreenKeypad(val digits: String, val toneCallId: Int?)

internal data class CallScreenAudioOutputs(
    val outputs: List<AudioOutput>,
    val selected: AudioOutput?,
)

internal enum class CallScreenSurface {
    Ongoing,
    Ringing,
}

internal data class CallScreenSurfaces(
    val ongoingCallId: Int? = null,
    val ringingCallId: Int? = null,
    val shown: CallScreenSurface? = null,
)

internal sealed interface CallScreenEntry {
    data object InCall : CallScreenEntry

    data class Ringing(val id: Int) : CallScreenEntry
}

internal sealed interface CallScreenView {
    val shownCall: PhoneCall

    data class InCall(
        val calls: CallScreenCalls,
        val keypad: CallScreenKeypad?,
        val isAudioOutputsShown: Boolean,
    ) : CallScreenView {
        override val shownCall: PhoneCall
            get() = calls.primary
    }

    data class Ringing(val call: PhoneCall, val canAnswerAndRelease: Boolean) : CallScreenView {
        override val shownCall: PhoneCall
            get() = call
    }
}

internal sealed interface CallAction {
    data class SetMuted(val isMuted: Boolean) : CallAction

    data class SetKeypadShown(val isShown: Boolean) : CallAction

    data class PlayDtmfTone(val id: Int, val digit: Char) : CallAction

    data object StopDtmfTone : CallAction

    data class SetAudioOutputsShown(val isShown: Boolean) : CallAction

    data class SelectAudioOutput(val output: AudioOutput) : CallAction

    data class Hold(val id: Int) : CallAction

    data class Unhold(val id: Int) : CallAction

    data class Merge(val id: Int) : CallAction

    data class SwapConference(val id: Int) : CallAction

    data class Disconnect(val id: Int) : CallAction

    data class Answer(val id: Int) : CallAction

    data class AnswerAndRelease(val id: Int) : CallAction

    data class Reject(val id: Int) : CallAction
}

internal data class CallButton(
    val action: CallAction,
    val isEnabled: Boolean,
    val isChecked: Boolean = false,
)

internal data class SoundButton(val button: CallButton, val icon: AudioOutputType)

internal data class CallScreenButtons(
    val mute: CallButton?,
    val keypad: CallButton,
    val sound: SoundButton,
    val holdOrMerge: CallButton?,
    val swap: CallButton?,
    val end: CallAction,
)

internal fun callScreenCalls(calls: List<PhoneCall>): CallScreenCalls? {
    val primary = firstInOrder(calls, PRIMARY_ORDER, ignored = null) ?: return null
    return CallScreenCalls(primary, firstInOrder(calls, SECONDARY_ORDER, ignored = primary))
}

internal fun callScreenEntry(call: PhoneCall): CallScreenEntry =
    if (call.status == CallStatus.Ringing) CallScreenEntry.Ringing(call.id)
    else CallScreenEntry.InCall

internal fun pushedCallScreen(
    stack: List<CallScreenEntry>,
    entry: CallScreenEntry,
    calls: List<PhoneCall>,
): List<CallScreenEntry> {
    val reconciled = reconciledCallScreen(stack, calls)
    if (reconciled.lastOrNull() == entry) return reconciled
    return reconciledCallScreen(reconciled + entry, calls)
}

internal fun reconciledCallScreen(
    stack: List<CallScreenEntry>,
    calls: List<PhoneCall>,
): List<CallScreenEntry> {
    val hasInCall = callScreenCalls(calls) != null
    val entries = stack.mapNotNull { entry ->
        when (entry) {
            CallScreenEntry.InCall -> entry.takeIf { hasInCall }
            is CallScreenEntry.Ringing -> {
                val call = calls.firstOrNull { it.id == entry.id }
                when {
                    call == null || !call.status.isLive -> null
                    call.status == CallStatus.Ringing -> entry
                    else -> CallScreenEntry.InCall
                }
            }
        }
    }
    return entries.filterIndexed { index, entry -> index == 0 || entry != entries[index - 1] }
}

internal fun reconciledKeypad(
    keypad: Keypad,
    previous: List<PhoneCall>,
    calls: List<PhoneCall>,
): Keypad {
    if (calls.isEmpty()) return Keypad()
    return if (callStartsRinging(previous, calls)) keypad.copy(isShown = false) else keypad
}

internal fun reconciledAudioOutputsShown(
    isShown: Boolean,
    previous: List<PhoneCall>,
    calls: List<PhoneCall>,
): Boolean = isShown && calls.isNotEmpty() && !callStartsRinging(previous, calls)

internal fun audioOutputsInOrder(outputs: List<AudioOutput>): List<AudioOutput> = outputs.sortedBy {
    AUDIO_OUTPUT_ORDER.indexOf(it.type)
}

internal fun callScreenSurfaces(
    top: CallScreenEntry?,
    popUpCall: PhoneCall?,
    calls: List<PhoneCall>,
): CallScreenSurfaces {
    val ringingIds = calls.filter { it.status == CallStatus.Ringing }.map { it.id }
    val ringingCallId =
        listOfNotNull((top as? CallScreenEntry.Ringing)?.id, popUpCall?.id).firstOrNull {
            it in ringingIds
        } ?: ringingIds.firstOrNull()
    val shown =
        when (top) {
            null -> null
            CallScreenEntry.InCall -> CallScreenSurface.Ongoing
            is CallScreenEntry.Ringing -> CallScreenSurface.Ringing
        }
    return CallScreenSurfaces(callScreenCalls(calls)?.primary?.id, ringingCallId, shown)
}

internal fun callScreenView(
    entry: CallScreenEntry?,
    calls: List<PhoneCall>,
    keypad: Keypad,
    isAudioOutputsShown: Boolean,
): CallScreenView? =
    when (entry) {
        null -> null
        CallScreenEntry.InCall ->
            callScreenCalls(calls)?.let { screenCalls ->
                CallScreenView.InCall(
                    screenCalls,
                    callScreenKeypad(screenCalls, calls, keypad),
                    isAudioOutputsShown && screenCalls.primary.status.isLive,
                )
            }
        is CallScreenEntry.Ringing ->
            calls
                .firstOrNull { it.id == entry.id && it.status == CallStatus.Ringing }
                ?.let { CallScreenView.Ringing(it, canAnswerAndRelease(calls, it)) }
    }

internal fun canAnswerAndRelease(calls: List<PhoneCall>, ringing: PhoneCall): Boolean = calls.any {
    it.status == CallStatus.Active && it.id != ringing.id
}

internal fun callScreenButtons(
    calls: CallScreenCalls,
    audio: CallAudio,
    isKeypadShown: Boolean,
): CallScreenButtons {
    val primary = calls.primary
    val secondary = calls.secondary
    val isEnabled = primary.status.isLive
    val isHeld = primary.status == CallStatus.Holding
    fun can(capability: Int) = primary.capabilities and capability == capability
    val canSwapConference = can(Call.Details.CAPABILITY_SWAP_CONFERENCE)
    val mute =
        if (can(Call.Details.CAPABILITY_MUTE)) {
            CallButton(CallAction.SetMuted(!audio.isMuted), isEnabled, isChecked = audio.isMuted)
        } else {
            null
        }
    val holdOrMerge =
        when {
            can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ->
                CallButton(CallAction.Merge(primary.id), isEnabled)
            !canSwapConference &&
                can(Call.Details.CAPABILITY_SUPPORT_HOLD or Call.Details.CAPABILITY_HOLD) ->
                CallButton(
                    if (isHeld) CallAction.Unhold(primary.id) else CallAction.Hold(primary.id),
                    isEnabled,
                    isChecked = isHeld,
                )
            else -> null
        }
    val swap =
        when {
            secondary != null ->
                CallButton(
                    CallAction.Unhold(secondary.id),
                    isEnabled && primary.status == CallStatus.Active,
                )
            canSwapConference -> CallButton(CallAction.SwapConference(primary.id), isEnabled)
            else -> null
        }
    val keypad =
        CallButton(CallAction.SetKeypadShown(!isKeypadShown), isEnabled, isChecked = isKeypadShown)
    return CallScreenButtons(
        mute,
        keypad,
        soundButton(audio, isEnabled),
        holdOrMerge,
        swap,
        CallAction.Disconnect(primary.id),
    )
}

private fun callStartsRinging(previous: List<PhoneCall>, calls: List<PhoneCall>): Boolean =
    calls.any { call ->
        call.status == CallStatus.Ringing &&
            previous.none { it.id == call.id && it.status == CallStatus.Ringing }
    }

private fun soundButton(audio: CallAudio, isEnabled: Boolean): SoundButton {
    val selected = audio.output?.type
    return SoundButton(
        CallButton(
            CallAction.SetAudioOutputsShown(true),
            isEnabled && audio.outputs.isNotEmpty(),
            isChecked = selected != null && selected != AudioOutputType.Bluetooth,
        ),
        selected ?: AudioOutputType.Speaker,
    )
}

private fun callScreenKeypad(
    screenCalls: CallScreenCalls,
    calls: List<PhoneCall>,
    keypad: Keypad,
): CallScreenKeypad? {
    if (!keypad.isShown || !screenCalls.primary.status.isLive) return null
    return CallScreenKeypad(keypad.digits, firstInOrder(calls, DTMF_ORDER, ignored = null)?.id)
}

private fun firstInOrder(
    calls: List<PhoneCall>,
    order: List<CallStatus>,
    ignored: PhoneCall?,
): PhoneCall? = order.firstNotNullOfOrNull { status ->
    calls.firstOrNull { it.status == status && it.id != ignored?.id }
}
