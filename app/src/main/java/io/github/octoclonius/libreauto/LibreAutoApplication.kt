package io.github.octoclonius.libreauto

import android.app.Application
import android.content.ComponentName
import io.github.octoclonius.libreauto.privileged.PrivilegedService
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection

open class LibreAutoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedServiceConnection.start(this, ComponentName(this, PrivilegedService::class.java))
    }
}
