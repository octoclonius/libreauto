package io.github.octoclonius.libreauto.projection

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.io.IOException
import kotlin.concurrent.thread

class DebugCarService : Service() {

    companion object {
        private const val TAG = "DebugCarService"
        private const val SOCKET_NAME = "libreauto"
        private const val CHANNEL_ID = "debug_car"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, DebugCarService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Can't start in the background; open LibreAuto to start it", e)
            }
        }
    }

    @Volatile private var server: LocalServerSocket? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (server == null) listen()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.let(::stopListening)
        super.onDestroy()
    }

    private fun listen() {
        val socket =
            try {
                LocalServerSocket(SOCKET_NAME)
            } catch (e: IOException) {
                Log.e(TAG, "Can't listen on @$SOCKET_NAME", e)
                stopSelf()
                return
            }
        server = socket
        val connection = projectionContainer.carConnection()
        thread(isDaemon = true, name = "debug-car") { acceptAll(socket, connection) }
        Log.i(TAG, "Listening on @$SOCKET_NAME")
    }

    private fun acceptAll(socket: LocalServerSocket, connection: CarConnection) {
        while (server === socket) {
            val client =
                try {
                    socket.accept()
                } catch (e: IOException) {
                    if (server === socket) Log.e(TAG, "Accept on @$SOCKET_NAME failed", e)
                    break
                }
            try {
                client.use { if (server === socket) accept(it, connection) }
            } catch (e: IOException) {
                Log.w(TAG, "Connection on @$SOCKET_NAME failed", e)
            }
        }
    }

    private fun accept(client: LocalSocket, connection: CarConnection) {
        val peer = client.peerCredentials
        if (peer.uid != Process.SHELL_UID) {
            Log.w(TAG, "Refused uid ${peer.uid} (pid ${peer.pid}) on @$SOCKET_NAME")
            return
        }
        Log.i(TAG, "Car connected on @$SOCKET_NAME (pid ${peer.pid})")
        connection.run(client.inputStream, client.outputStream)
    }

    private fun stopListening(socket: LocalServerSocket) {
        server = null
        try {
            LocalSocket().use { it.connect(LocalSocketAddress(SOCKET_NAME)) }
        } catch (_: IOException) {}
        try {
            socket.close()
        } catch (_: IOException) {}
        Log.i(TAG, "Stopped listening on @$SOCKET_NAME")
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.debug_car_channel),
                    NotificationManager.IMPORTANCE_MIN,
                )
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.debug_car_title))
            .setContentText(getString(R.string.debug_car_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .build()
    }
}
