package io.github.octoclonius.libreauto.projection.media

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

internal fun AudioManager.pressMediaKey(keyCode: Int) {
    val now = SystemClock.uptimeMillis()
    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
        dispatchMediaKeyEvent(KeyEvent(now, now, action, keyCode, 0))
    }
}
