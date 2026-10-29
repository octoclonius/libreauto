package io.github.octoclonius.libreauto.projection.display

internal data class OwnedDisplay<S : Any>(val service: S, val id: Int)

internal data class DisplayChange<S : Any>(val release: OwnedDisplay<S>?, val createOn: S?)

internal fun <S : Any> displayChange(current: OwnedDisplay<S>?, service: S?): DisplayChange<S> =
    if (current != null && current.service === service) {
        DisplayChange(release = null, createOn = null)
    } else {
        DisplayChange(release = current, createOn = service)
    }
