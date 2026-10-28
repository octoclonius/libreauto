package io.github.octoclonius.libreauto.session.calls

import io.github.octoclonius.libreauto.privileged.IPrivilegedService
import io.github.octoclonius.libreauto.session.privileged.call

internal object PrivilegedCalls {

    fun allowAccess(service: IPrivilegedService) {
        service.call("allow the phone's calls") { it.allowCallAccess() }
    }
}
