package io.github.octoclonius.libreauto.session.media

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.github.octoclonius.libreauto.session.jsonDataStore
import io.github.octoclonius.libreauto.session.pressMediaKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer

internal class MediaResumption(context: Context, private val externalScope: CoroutineScope) {

    private companion object {
        const val TAG = "MediaResumption"
        const val NAME = "media_resumption"
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val dataStore = jsonDataStore(context, NAME, Boolean.serializer(), false)

    fun resume() {
        externalScope.launch {
            var wasOwed = false
            dataStore.updateData { isOwed ->
                wasOwed = isOwed
                false
            }
            if (!wasOwed) return@launch
            Log.i(TAG, "Resuming the media paused when the car went away")
            audioManager.pressMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    fun pause() {
        externalScope.launch { dataStore.updateData { true } }
        Log.i(TAG, "Pausing media until the car is back")
        audioManager.pressMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun forget() {
        externalScope.launch { dataStore.updateData { false } }
        Log.i(TAG, "Nothing plays on the car; nothing to resume next time")
    }
}
