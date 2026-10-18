package io.github.octoclonius.libreauto.privileged

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit

internal class CompanionAssociation(private val context: Context) {

    companion object {
        private const val TAG = "CompanionAssociation"
        private const val PROFILE = AssociationRequest.DEVICE_PROFILE_APP_STREAMING
        private const val PER_USER_RANGE = 100_000
        private const val COMMAND_TIMEOUT_SECONDS = 10L
        private const val STORE_TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 100L
        private const val SELF_MANAGED = "true"
    }

    private val manager: CompanionDeviceManager =
        SystemServices.manager(
            CompanionDeviceManager::class.java,
            "android.companion.ICompanionDeviceManager",
            Context.COMPANION_DEVICE_SERVICE,
            context,
        )

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
                (Process.myUid() / PER_USER_RANGE).toString(),
                context.packageName,
                ASSOCIATION_ADDRESS,
                PROFILE,
                SELF_MANAGED,
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "${command.joinToString(" ")} didn't finish"
        }
        check(process.exitValue() == 0) {
            "${command.joinToString(" ")} exited with ${process.exitValue()}: $output"
        }
    }
}
