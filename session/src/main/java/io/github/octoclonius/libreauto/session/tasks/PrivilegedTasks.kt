package io.github.octoclonius.libreauto.session.tasks

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

object PrivilegedTasks {

    fun show(displayId: Int, taskId: Int) {
        PrivilegedServiceConnection.call("show the task $taskId") {
            it.showTask(displayId, taskId)
        }
    }

    fun close(displayId: Int, taskId: Int) {
        PrivilegedServiceConnection.call("close the task $taskId") {
            it.closeTask(displayId, taskId)
        }
    }
}
