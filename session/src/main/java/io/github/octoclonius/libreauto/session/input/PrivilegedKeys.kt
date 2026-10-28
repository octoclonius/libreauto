package io.github.octoclonius.libreauto.session.input

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedKeys {

    fun send(displayId: Int, keyCode: Int) {
        PrivilegedServiceConnection.call("take key $keyCode") { it.sendKey(displayId, keyCode) }
    }
}
