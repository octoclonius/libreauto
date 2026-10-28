package io.github.octoclonius.libreauto.session

import android.content.Context
import io.github.octoclonius.libreauto.session.calls.CallRepository

class SessionContainer(context: Context) {

    val carSessionHolder = CarSessionHolder()
    val callRepository = CallRepository(context)

    interface Provider {
        val sessionContainer: SessionContainer
    }
}

val Context.sessionContainer: SessionContainer
    get() = (applicationContext as SessionContainer.Provider).sessionContainer
