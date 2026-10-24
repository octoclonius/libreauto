package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.UiConfig
import io.github.octoclonius.libreauto.protocol.control.UiCutout
import io.github.octoclonius.libreauto.protocol.control.UiInsets
import io.github.octoclonius.libreauto.protocol.control.UiRect
import java.net.ProtocolException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UiConfigUpdateTest {

    private val old = ProtocolVersion(4, 2)
    private val replacing = ProtocolVersion(4, 3)

    @Test
    fun theUpdatedUiConfigIsField1() {
        assertEquals(
            UiConfig(content_insets = UiInsets(top = 10)),
            parseUiConfigUpdate(bytes(0x0a, 0x04, 0x12, 0x02, 0x08, 0x0a)),
        )
    }

    @Test
    fun anUpdateWithoutAUiConfigIsAProtocolError() {
        assertThrows<ProtocolException> { parseUiConfigUpdate(ByteArray(0)) }
    }

    @Test
    fun theReplyHasTheUiConfigInUseAsField1() {
        assertArrayEquals(
            bytes(0x0a, 0x04, 0x12, 0x02, 0x08, 0x0a),
            uiConfigUpdateReply(UiConfig(content_insets = UiInsets(top = 10))),
        )
    }

    @Test
    fun fromProtocol4Point3AnUpdateReplacesTheUiConfig() {
        val current =
            UiConfig(stable_insets = UiInsets(top = 10), content_insets = UiInsets(top = 20))
        val update = UiConfig(content_insets = UiInsets(bottom = 30))
        assertEquals(update, updatedUiConfig(current, update, replacing))
    }

    @Test
    fun beforeProtocol4Point3AnUpdateIsMergedOntoTheUiConfig() {
        val current =
            UiConfig(
                margins = UiInsets(top = 0, bottom = 0, left = 40, right = 40),
                content_insets = UiInsets(top = 20, bottom = 10),
                stable_insets = UiInsets(top = 10),
                cutouts = listOf(UiCutout(UiRect(top = 0, bottom = 50, left = 0, right = 100))),
            )
        val update =
            UiConfig(
                content_insets = UiInsets(top = 60),
                cutouts = listOf(UiCutout(UiRect(top = 0, bottom = 50, left = 700, right = 800))),
            )
        assertEquals(
            UiConfig(
                margins = UiInsets(top = 0, bottom = 0, left = 40, right = 40),
                content_insets = UiInsets(top = 60, bottom = 10),
                stable_insets = UiInsets(top = 10),
                cutouts = current.cutouts + update.cutouts,
            ),
            updatedUiConfig(current, update, old),
        )
    }

    @Test
    fun theUiConfigInUseHasTheMarginsInUse() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                margins = CarInsets(top = 0, bottom = 0, left = 40, right = 40),
            )
        val config =
            UiConfig(
                margins = UiInsets(top = 0, bottom = 0, left = 10, right = 10),
                content_insets = UiInsets(top = 20),
            )
        assertEquals(
            UiConfig(
                margins = UiInsets(top = 0, bottom = 0, left = 40, right = 40),
                content_insets = UiInsets(top = 20),
            ),
            uiConfigInUse(format, config),
        )
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
