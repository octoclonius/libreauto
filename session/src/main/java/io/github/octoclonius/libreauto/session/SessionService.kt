package io.github.octoclonius.libreauto.session

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import io.github.octoclonius.libreauto.session.usb.AccessoryLink
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.usb.AccessorySessions
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SessionService : Service() {

    companion object {
        private const val TAG = "SessionService"
        private const val CHANNEL_ID = "car_session"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context, accessory: UsbAccessory) {
            context.startService(
                Intent(context, SessionService::class.java)
                    .putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
            )
        }
    }

    private lateinit var connection: CarConnection
    private lateinit var executor: ExecutorService
    private lateinit var sessions: AccessorySessions<UsbAccessory>

    private val detachReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val accessory = intent.accessory() ?: return
                Log.i(TAG, "Car detached: ${accessory.model}")
                sessions.detach(accessory)
            }
        }

    override fun onCreate() {
        super.onCreate()
        connection = CarConnection(this)
        executor =
            Executors.newSingleThreadExecutor { Thread(it, "usb-car").apply { isDaemon = true } }
        sessions = AccessorySessions(executor, ::stopSelf, ::leaveAccessoryMode)
        registerReceiver(
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED),
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accessory = intent?.accessory()
        if (accessory == null) {
            Log.w(TAG, "Started without an accessory")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't run in the foreground without access to ${accessory.model}", e)
            stopSelf(startId)
            return START_NOT_STICKY
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Can't run in the foreground from the background", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        Log.i(TAG, "Car attached: ${accessory.model}")
        sessions.start(startId, accessory, ::open)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(detachReceiver)
        sessions.close()
        executor.shutdown()
        super.onDestroy()
    }

    private fun leaveAccessoryMode() {
        val service = PrivilegedServiceConnection.service.value
        if (service == null) {
            Log.w(TAG, "Can't leave accessory mode while the privileged service isn't running")
            return
        }
        try {
            service.exitAccessoryMode()
            Log.i(TAG, "Leaving accessory mode so the car can connect again")
        } catch (e: RemoteException) {
            Log.w(TAG, "The privileged service didn't leave accessory mode", e)
        }
    }

    private fun open(accessory: UsbAccessory): AccessoryLink? {
        val fd =
            try {
                getSystemService(UsbManager::class.java).openAccessory(accessory)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Can't open ${accessory.model}", e)
                return null
            }
        if (fd == null) {
            Log.w(TAG, "Can't open ${accessory.model}")
            return null
        }
        return FileDescriptorLink(fd)
    }

    private inner class FileDescriptorLink(private val fd: ParcelFileDescriptor) : AccessoryLink {
        override fun run(): Boolean {
            return connection.run(
                FileInputStream(fd.fileDescriptor),
                FileOutputStream(fd.fileDescriptor),
            )
        }

        override fun close() {
            try {
                fd.close()
            } catch (_: IOException) {}
        }
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.session_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val launch =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.session_title))
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }
}

private fun Intent.accessory(): UsbAccessory? =
    getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
