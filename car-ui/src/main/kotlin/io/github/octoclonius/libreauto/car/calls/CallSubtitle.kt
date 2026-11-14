package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.CallerNumber

internal sealed interface CallSubtitle {
    data class App(val name: String) : CallSubtitle

    data class Number(val number: String, val label: String?) : CallSubtitle

    data class Location(val location: String) : CallSubtitle
}

internal fun callSubtitle(
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
