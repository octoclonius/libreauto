package io.github.octoclonius.libreauto.privileged

import android.view.KeyEvent

private val KEY_CODES = setOf(KeyEvent.KEYCODE_BACK)

internal fun keyRequestError(keyCode: Int): String? =
    if (keyCode !in KEY_CODES) "The key code $keyCode isn't one that may be sent" else null
