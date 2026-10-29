package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.UiConfig
import io.github.octoclonius.libreauto.protocol.control.UiInsets
import java.io.IOException
import java.net.ProtocolException

private val FIRST_VERSION_REPLACING_UI_CONFIG = ProtocolVersion(4, 3)

@Throws(IOException::class)
fun parseUiConfigUpdate(body: ByteArray): UiConfig =
    UiConfigUpdate.ADAPTER.decode(body).config
        ?: throw ProtocolException("The car's UI config update has no UI config")

fun uiConfigUpdateReply(config: UiConfig): ByteArray = UiConfigUpdate(config = config).encode()

fun updatedUiConfig(current: UiConfig, update: UiConfig, car: ProtocolVersion): UiConfig =
    if (car >= FIRST_VERSION_REPLACING_UI_CONFIG) {
        update
    } else {
        UiConfig(
            margins = merged(current.margins, update.margins),
            content_insets = merged(current.content_insets, update.content_insets),
            stable_insets = merged(current.stable_insets, update.stable_insets),
            cutouts = current.cutouts + update.cutouts,
        )
    }

fun uiConfigInUse(format: VideoFormat, config: UiConfig): UiConfig =
    UiConfig(
        margins = uiInsets(format.margins),
        content_insets = config.content_insets,
        stable_insets = config.stable_insets,
        cutouts = config.cutouts,
    )

private fun merged(current: UiInsets?, update: UiInsets?): UiInsets? {
    if (current == null || update == null) return update ?: current
    return UiInsets(
        top = update.top ?: current.top,
        bottom = update.bottom ?: current.bottom,
        left = update.left ?: current.left,
        right = update.right ?: current.right,
    )
}

private fun uiInsets(insets: CarInsets): UiInsets =
    UiInsets(top = insets.top, bottom = insets.bottom, left = insets.left, right = insets.right)
