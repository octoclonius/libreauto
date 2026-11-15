package io.github.octoclonius.libreauto.car.calls

import android.telecom.Call
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

internal data class CallScreenCalls(val primary: PhoneCall, val secondary: PhoneCall?)

internal sealed interface CallAction {
    data class SetMuted(val isMuted: Boolean) : CallAction

    data class Hold(val id: Int) : CallAction

    data class Unhold(val id: Int) : CallAction

    data class Merge(val id: Int) : CallAction

    data class SwapConference(val id: Int) : CallAction

    data class Disconnect(val id: Int) : CallAction
}

internal data class CallButton(
    val action: CallAction,
    val isEnabled: Boolean,
    val isChecked: Boolean = false,
)

internal data class CallScreenButtons(
    val mute: CallButton?,
    val holdOrMerge: CallButton?,
    val swap: CallButton?,
    val end: CallAction,
)

internal fun callScreenCalls(calls: List<PhoneCall>): CallScreenCalls? {
    val primary = firstInOrder(calls, PRIMARY_ORDER, ignored = null) ?: return null
    return CallScreenCalls(primary, firstInOrder(calls, SECONDARY_ORDER, ignored = primary))
}

internal fun callScreenButtons(calls: CallScreenCalls, isMuted: Boolean): CallScreenButtons {
    val primary = calls.primary
    val secondary = calls.secondary
    val isEnabled = primary.status.isLive
    val isHeld = primary.status == CallStatus.Holding
    fun can(capability: Int) = primary.capabilities and capability == capability
    val canSwapConference = can(Call.Details.CAPABILITY_SWAP_CONFERENCE)
    val mute =
        if (can(Call.Details.CAPABILITY_MUTE)) {
            CallButton(CallAction.SetMuted(!isMuted), isEnabled, isChecked = isMuted)
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
    return CallScreenButtons(mute, holdOrMerge, swap, CallAction.Disconnect(primary.id))
}

private fun firstInOrder(
    calls: List<PhoneCall>,
    order: List<CallStatus>,
    ignored: PhoneCall?,
): PhoneCall? = order.firstNotNullOfOrNull { status ->
    calls.firstOrNull { it.status == status && it.id != ignored?.id }
}
