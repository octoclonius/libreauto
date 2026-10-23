package io.github.octoclonius.libreauto.privileged

import android.view.KeyEvent

private val KEY_CODES = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME)

internal fun keyRequestError(displayId: Int, keyCode: Int, carDisplayId: Int?): String? =
    when {
        carDisplayId == null -> "There is no car display"
        displayId != carDisplayId -> "The display $displayId isn't the car display $carDisplayId"
        keyCode !in KEY_CODES -> "The key code $keyCode isn't one that may be sent"
        else -> null
    }
