package io.github.octoclonius.libreauto.session.video

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedHome {

    fun show(displayId: Int) {
        PrivilegedServiceConnection.call("show the home") {
            it.showActivity(displayId, PrivilegedServiceConnection.carHomeIntent)
        }
    }
}
