package io.github.octoclonius.libreauto.privileged

import android.os.Process
import kotlin.system.exitProcess

class PrivilegedService : IPrivilegedService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()
}
