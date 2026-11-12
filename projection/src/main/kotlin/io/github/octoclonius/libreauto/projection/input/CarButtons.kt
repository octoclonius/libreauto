package io.github.octoclonius.libreauto.projection.input

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.media.pressMediaKey
import io.github.octoclonius.libreauto.protocol.input.CarButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class NavigationButton {
    Home,
    Back,
}

class CarButtons internal constructor(context: Context, private val calls: CallRepository) {

    private companion object {
        const val TAG = "CarButtons"
        const val BUFFER = 8

        val MEDIA_KEYS =
            mapOf(
                CarButton.PLAY_PAUSE to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                CarButton.NEXT to KeyEvent.KEYCODE_MEDIA_NEXT,
                CarButton.PREVIOUS to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                CarButton.PLAY to KeyEvent.KEYCODE_MEDIA_PLAY,
                CarButton.PAUSE to KeyEvent.KEYCODE_MEDIA_PAUSE,
            )
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _presses = MutableSharedFlow<NavigationButton>(extraBufferCapacity = BUFFER)
    val presses: SharedFlow<NavigationButton> = _presses.asSharedFlow()

    fun onPress(code: Int) {
        val mediaKey = MEDIA_KEYS[code]
        if (mediaKey != null) {
            audioManager.pressMediaKey(mediaKey)
        } else if (code == CarButton.HOME) {
            _presses.tryEmit(NavigationButton.Home)
        } else if (code == CarButton.BACK) {
            _presses.tryEmit(NavigationButton.Back)
        } else if (code == CarButton.CALL) {
            Log.i(TAG, "The car's call button answers the ringing call, if any")
            calls.acceptRingingCall()
        } else if (code == CarButton.END_CALL) {
            Log.i(TAG, "The car's end call button ends the foreground call, if any")
            calls.endCall()
        }
    }
}
