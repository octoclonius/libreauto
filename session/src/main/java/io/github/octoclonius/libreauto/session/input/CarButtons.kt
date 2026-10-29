package io.github.octoclonius.libreauto.session.input

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.github.octoclonius.libreauto.protocol.input.CarButton
import io.github.octoclonius.libreauto.session.calls.CallRepository
import io.github.octoclonius.libreauto.session.media.pressMediaKey
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
        } else if (code == CarButton.CALL || code == CarButton.END_CALL) {
            onCallButton(code)
        }
    }

    private fun onCallButton(code: Int) {
        val call = calls.state.value
        when {
            code == CarButton.CALL && call.isRinging -> {
                Log.i(TAG, "Answering the ringing call for the car's call button")
                calls.answer()
            }
            code == CarButton.END_CALL && call.isInCall -> {
                Log.i(TAG, "Ending the call for the car's end call button")
                calls.end()
            }
            else -> Log.i(TAG, "The car's call button $code without a call to act on")
        }
    }
}
