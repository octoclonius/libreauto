package io.github.octoclonius.libreauto.privileged.settings

import io.github.octoclonius.libreauto.privileged.api.PhoneSetting

private val SETTINGS =
    setOf(
        PhoneSetting.WIFI,
        PhoneSetting.BLUETOOTH,
        PhoneSetting.MOBILE_DATA,
        PhoneSetting.AIRPLANE_MODE,
        PhoneSetting.DO_NOT_DISTURB,
        PhoneSetting.LOCATION,
    )

internal fun settingRequestError(setting: Int): String? =
    if (setting in SETTINGS) null else "The setting $setting isn't one that may be changed"
