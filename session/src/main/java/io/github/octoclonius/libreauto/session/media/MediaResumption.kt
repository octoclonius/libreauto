package io.github.octoclonius.libreauto.session.media

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.github.octoclonius.libreauto.session.pressMediaKey

internal class MediaResumption(context: Context) {

    companion object {
        private const val TAG = "MediaResumption"
        private const val PREFS = "media_resumption"
        private const val PREF_IS_RESUME_OWED = "is_resume_owed"
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun resume() {
        if (!prefs.getBoolean(PREF_IS_RESUME_OWED, false)) return
        prefs.edit().remove(PREF_IS_RESUME_OWED).apply()
        Log.i(TAG, "Resuming the media paused when the car went away")
        audioManager.pressMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun pause() {
        prefs.edit().putBoolean(PREF_IS_RESUME_OWED, true).apply()
        Log.i(TAG, "Pausing media until the car is back")
        audioManager.pressMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun forget() {
        prefs.edit().remove(PREF_IS_RESUME_OWED).apply()
        Log.i(TAG, "Nothing plays on the car; nothing to resume next time")
    }
}
