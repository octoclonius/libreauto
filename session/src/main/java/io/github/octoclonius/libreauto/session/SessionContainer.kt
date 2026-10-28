package io.github.octoclonius.libreauto.session

import android.content.Context

class SessionContainer {

    val carSessionHolder = CarSessionHolder()

    interface Provider {
        val sessionContainer: SessionContainer
    }
}

val Context.sessionContainer: SessionContainer
    get() = (applicationContext as SessionContainer.Provider).sessionContainer
