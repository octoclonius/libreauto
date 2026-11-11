package io.github.octoclonius.libreauto.privileged.display

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.octoclonius.libreauto.privileged.SystemServices
import io.github.octoclonius.libreauto.privileged.userId
import java.util.concurrent.TimeUnit

internal class CompanionAssociation(private val context: Context) {

    companion object {
        private const val TAG = "CompanionAssociation"
        private const val PROFILE = AssociationRequest.DEVICE_PROFILE_APP_STREAMING
        private const val COMMAND_TIMEOUT_SECONDS = 5L
        private const val STORE_TIMEOUT_MILLIS = 5_000L
        private const val POLL_INTERVAL_MILLIS = 100L
        private const val SELF_MANAGED = "true"
    }

    private val manager: CompanionDeviceManager = SystemServices.companionDeviceManager(context)

    fun acquire(): Int {
        ours()?.let {
            Log.i(TAG, "Reusing the companion association $it")
            return it
        }
        associate()
        val deadline = SystemClock.uptimeMillis() + STORE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            ours()?.let {
                Log.i(TAG, "Created the companion association $it")
                return it
            }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        error("No companion association after ${STORE_TIMEOUT_MILLIS}ms: the role wasn't granted")
    }

    fun release(id: Int) {
        manager.disassociate(id)
        Log.i(TAG, "Removed the companion association $id")
    }

    private fun ours(): Int? =
        ourAssociationId(
            manager.myAssociations.map {
                AssociationEntry(it.id, it.deviceProfile, it.deviceMacAddress?.toString())
            },
            PROFILE,
        )

    private fun associate() {
        val command =
            listOf(
                "cmd",
                "companiondevice",
                "associate",
                Process.myUserHandle().userId.toString(),
                context.packageName,
                ASSOCIATION_ADDRESS,
                PROFILE,
                SELF_MANAGED,
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("${command.joinToString(" ")} didn't finish in ${COMMAND_TIMEOUT_SECONDS}s")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.exitValue() == 0) {
            "${command.joinToString(" ")} exited with ${process.exitValue()}: $output"
        }
    }
}
