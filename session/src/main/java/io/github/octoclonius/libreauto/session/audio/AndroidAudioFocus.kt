package io.github.octoclonius.libreauto.session.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

internal class AndroidAudioFocus(context: Context) {

    companion object {
        private const val TAG = "AndroidAudioFocus"
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_UNKNOWN).build()
    private var request: AudioFocusRequest? = null

    fun request(gain: Int) {
        abandon()
        val created =
            AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener {}
                .build()
        request = created
        val result = audioManager.requestAudioFocus(created)
        Log.i(TAG, "Requested Android audio focus $gain for the car: $result")
    }

    fun abandon() {
        val held = request ?: return
        request = null
        audioManager.abandonAudioFocusRequest(held)
        Log.i(TAG, "Abandoned Android audio focus for the car")
    }
}
