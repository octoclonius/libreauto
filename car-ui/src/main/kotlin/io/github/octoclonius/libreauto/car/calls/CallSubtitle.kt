package io.github.octoclonius.libreauto.car.calls

import android.text.format.DateUtils
import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.CallerNumber

internal sealed interface CallSubtitle {
    data class App(val name: String) : CallSubtitle

    data class Number(val number: String, val label: String?) : CallSubtitle

    data class Location(val location: String) : CallSubtitle

    data object Dialing : CallSubtitle

    data class Ongoing(val connectTimeMillis: Long) : CallSubtitle

    data object OnHold : CallSubtitle
}

internal fun callSubtitle(
    status: CallStatus,
    connectTimeMillis: Long,
    caller: Caller,
    isAppCall: Boolean,
    appName: String?,
    numberLabel: (CallerNumber) -> String?,
    location: (CallerNumber) -> String?,
): CallSubtitle? =
    when (status) {
        CallStatus.Ringing -> callerSubtitle(caller, isAppCall, appName, numberLabel, location)
        CallStatus.Dialing -> CallSubtitle.Dialing
        CallStatus.Active -> CallSubtitle.Ongoing(connectTimeMillis)
        CallStatus.Holding -> CallSubtitle.OnHold
    }

internal fun elapsedSeconds(connectTimeMillis: Long, nowMillis: Long): Long =
    maxOf(0L, (nowMillis - connectTimeMillis) / DateUtils.SECOND_IN_MILLIS)

private fun callerSubtitle(
    caller: Caller,
    isAppCall: Boolean,
    appName: String?,
    numberLabel: (CallerNumber) -> String?,
    location: (CallerNumber) -> String?,
): CallSubtitle? {
    if (isAppCall) return appName?.let(CallSubtitle::App)
    return when (caller) {
        is Caller.Contact ->
            caller.number?.let { CallSubtitle.Number(it.formatted, numberLabel(it)) }
        is Caller.Named -> caller.number?.let { CallSubtitle.Number(it.formatted, null) }
        is Caller.Number -> location(caller.number)?.ifBlank { null }?.let(CallSubtitle::Location)
        Caller.Private,
        Caller.Payphone,
        Caller.Unknown -> null
    }
}
