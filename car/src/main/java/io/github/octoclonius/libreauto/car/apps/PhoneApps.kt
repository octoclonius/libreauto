package io.github.octoclonius.libreauto.car.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.icu.text.AlphabeticIndex
import android.icu.text.AlphabeticIndex.Bucket.LabelType.NORMAL
import android.icu.text.AlphabeticIndex.Bucket.LabelType.UNDERFLOW
import android.os.LocaleList
import io.github.octoclonius.libreauto.session.video.CarDisplay
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

private const val LEGACY_ICON_SIZE_DP = 48f
private const val ADAPTIVE_ICON_LAYER_SIZE_DP = 108f
private const val UNDERFLOW_LABEL = "#"

class PhoneApp(val component: ComponentName, val label: String, val icon: Drawable)

class AppSection(val label: String, val apps: List<PhoneApp>)

fun phoneApps(context: Context): Flow<List<AppSection>> =
    combine(packageChanges(context), carDensities()) { _, carDensity -> carDensity }
        .buffer(Channel.CONFLATED)
        .map { launcherApps(context, it) }
        .flowOn(Dispatchers.IO)

private fun carDensities(): Flow<Int> =
    CarDisplay.state.mapNotNull { it?.carDensity }.distinctUntilChanged()

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

private fun launcherApps(context: Context, carDensity: Int): List<AppSection> {
    val packageManager = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val index = appIndex()
    packageManager
        .queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        .filter { it.activityInfo.packageName != context.packageName }
        .forEach {
            val app =
                PhoneApp(
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    label = it.loadLabel(packageManager).toString(),
                    icon = it.loadCarIcon(packageManager, carDensity),
                )
            index.addRecord(app.label, app)
        }
    return index.mapNotNull { bucket ->
        val apps = bucket.map { it.data }
        val type = bucket.labelType
        if (apps.isEmpty() && type != NORMAL && type != UNDERFLOW) null
        else AppSection(bucket.label, apps)
    }
}

private fun appIndex(): AlphabeticIndex<PhoneApp> {
    val locales = LocaleList.getDefault()
    val additions = (1 until locales.size()).map { locales[it] } + Locale.ENGLISH
    return AlphabeticIndex<PhoneApp>(locales[0])
        .addLabels(*additions.toTypedArray())
        .setUnderflowLabel(UNDERFLOW_LABEL)
}

private fun ResolveInfo.loadCarIcon(packageManager: PackageManager, carDensity: Int): Drawable {
    if (iconResource == 0) return loadIcon(packageManager)
    return try {
        val resources = packageManager.getResourcesForApplication(activityInfo.applicationInfo)
        val legacyDensity = iconDensity(carDensity, APP_ICON_SIZE.value / LEGACY_ICON_SIZE_DP)
        when (val icon = resources.getDrawableForDensity(iconResource, legacyDensity, null)) {
            null -> loadIcon(packageManager)
            is AdaptiveIconDrawable -> {
                val layerScale =
                    APP_ICON_SIZE.value * adaptiveIconLayerScale() / ADAPTIVE_ICON_LAYER_SIZE_DP
                val layerDensity = iconDensity(carDensity, layerScale)
                resources.getDrawableForDensity(iconResource, layerDensity, null) ?: icon
            }
            else -> icon
        }
    } catch (e: PackageManager.NameNotFoundException) {
        loadIcon(packageManager)
    } catch (e: Resources.NotFoundException) {
        loadIcon(packageManager)
    }
}

private fun adaptiveIconLayerScale(): Float =
    1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction()

private fun iconDensity(carDensity: Int, scale: Float): Int = ceil(carDensity * scale).toInt()
