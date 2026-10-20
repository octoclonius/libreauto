package io.github.octoclonius.libreauto.car.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class PhoneApp(val component: ComponentName, val label: String, val icon: Drawable)

fun phoneApps(context: Context): Flow<List<PhoneApp>> =
    packageChanges(context)
        .buffer(Channel.CONFLATED)
        .map { launcherApps(context) }
        .flowOn(Dispatchers.IO)

private fun packageChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
    context.registerReceiver(
        receiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        },
        Context.RECEIVER_NOT_EXPORTED,
    )
    trySend(Unit)
    awaitClose { context.unregisterReceiver(receiver) }
}

private fun launcherApps(context: Context): List<PhoneApp> {
    val packageManager = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val collator = Collator.getInstance()
    return packageManager
        .queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            PhoneApp(
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                label = it.loadLabel(packageManager).toString(),
                icon = it.loadIcon(packageManager),
            )
        }
        .sortedWith(compareBy(collator) { it.label })
}
