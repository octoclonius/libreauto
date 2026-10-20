package io.github.octoclonius.libreauto.session.input

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import io.github.octoclonius.libreauto.protocol.input.CarButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class NavigationButton {
    Home,
    Back,
}

class CarButtons(context: Context) {

    companion object {
        private const val BUFFER = 8

        private val _presses = MutableSharedFlow<NavigationButton>(extraBufferCapacity = BUFFER)
        val presses: SharedFlow<NavigationButton> = _presses.asSharedFlow()

        private val MEDIA_KEYS =
            mapOf(
                CarButton.PLAY_PAUSE to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                CarButton.NEXT to KeyEvent.KEYCODE_MEDIA_NEXT,
                CarButton.PREVIOUS to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                CarButton.PLAY to KeyEvent.KEYCODE_MEDIA_PLAY,
                CarButton.PAUSE to KeyEvent.KEYCODE_MEDIA_PAUSE,
            )
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun onPress(code: Int) {
        val mediaKey = MEDIA_KEYS[code]
        if (mediaKey != null) {
            val now = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, action, mediaKey, 0))
            }
        } else if (code == CarButton.HOME) {
            _presses.tryEmit(NavigationButton.Home)
        } else if (code == CarButton.BACK) {
            _presses.tryEmit(NavigationButton.Back)
        }
    }
}
