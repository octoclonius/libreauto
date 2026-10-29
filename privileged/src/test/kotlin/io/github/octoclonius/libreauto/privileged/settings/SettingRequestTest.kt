package io.github.octoclonius.libreauto.privileged.settings

import io.github.octoclonius.libreauto.privileged.api.PhoneSetting
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingRequestTest {

    @Test
    fun everyQuickSettingIsAccepted() {
        listOf(
                PhoneSetting.WIFI,
                PhoneSetting.BLUETOOTH,
                PhoneSetting.MOBILE_DATA,
                PhoneSetting.AIRPLANE_MODE,
                PhoneSetting.DO_NOT_DISTURB,
                PhoneSetting.LOCATION,
            )
            .forEach { assertNull(settingRequestError(it)) }
    }

    @Test
    fun anUnknownSettingIsRefused() {
        assertNotNull(settingRequestError(0))
        assertNotNull(settingRequestError(7))
    }
}
