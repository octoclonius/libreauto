package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

internal object PrivilegedTouches {

    fun send(eventTimeNanos: Long, touch: Touch) {
        PrivilegedServiceConnection.call("take a touch") {
            it.sendTouch(
                eventTimeNanos,
                touch.action,
                touch.actionIndex,
                IntArray(touch.pointers.size) { index -> touch.pointers[index].id },
                IntArray(touch.pointers.size) { index -> touch.pointers[index].x },
                IntArray(touch.pointers.size) { index -> touch.pointers[index].y },
            )
        }
    }
}
