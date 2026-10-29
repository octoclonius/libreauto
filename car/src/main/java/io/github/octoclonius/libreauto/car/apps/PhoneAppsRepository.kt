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
import androidx.compose.ui.unit.Dp
import io.github.octoclonius.libreauto.car.home.HOME_ICON_SIZE
import io.github.octoclonius.libreauto.car.home.HomeAppsRepository
import io.github.octoclonius.libreauto.session.CarSessionHolder
import io.github.octoclonius.libreauto.session.display.CarDisplay
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val LEGACY_ICON_SIZE_DP = 48f
private const val ADAPTIVE_ICON_LAYER_SIZE_DP = 108f
private const val UNDERFLOW_LABEL = "#"

class PhoneApp(val component: ComponentName, val label: String, val icon: Drawable)

class AppSection(val label: String, val apps: List<PhoneApp>)

private class LoadedApps(val sections: List<AppSection>, val gone: Set<ComponentName>)

class PhoneAppsRepository(
    context: Context,
    private val homeApps: HomeAppsRepository,
    private val carSessionHolder: CarSessionHolder,
) {
    private val context = context.applicationContext
    private val scope = MainScope()

    private val _sections = MutableStateFlow<List<AppSection>?>(null)
    val sections: StateFlow<List<AppSection>?> = _sections.asStateFlow()

    fun start() {
        scope.launch {
            combine(packageChanges(context), currentCarDensities(carSessionHolder)) { _, density ->
                    density
                }
                .buffer(Channel.CONFLATED)
                .map { carDensity ->
                    val sections = launcherApps(context, carDensity)
                    LoadedApps(
                        sections,
                        goneComponents(context, homeApps.components.value, sections),
                    )
                }
                .flowOn(Dispatchers.IO)
                .collect {
                    _sections.value = it.sections
                    homeApps.removeAll(it.gone)
                }
        }
    }
}

internal fun carDensities(display: CarDisplay): Flow<Int> =
    display.carDensity.filterNotNull().distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
private fun currentCarDensities(carSessionHolder: CarSessionHolder): Flow<Int> =
    carSessionHolder.session
        .flatMapLatest { it?.displays?.car?.carDensity ?: flowOf(null) }
        .filterNotNull()
        .distinctUntilChanged()

private fun goneComponents(
    context: Context,
    components: List<ComponentName>,
    sections: List<AppSection>,
): Set<ComponentName> {
    val shown = sections.flatMapTo(HashSet()) { section -> section.apps.map { it.component } }
    return components
        .filter { it !in shown }
        .filterTo(HashSet()) {
            try {
                context.packageManager.getActivityInfo(it, PackageManager.ComponentInfoFlags.of(0))
                false
            } catch (e: PackageManager.NameNotFoundException) {
                true
            }
        }
}

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
                    icon = it.loadCarIcon(packageManager, carDensity, HOME_ICON_SIZE),
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

internal fun ResolveInfo.loadCarIcon(
    packageManager: PackageManager,
    carDensity: Int,
    size: Dp,
): Drawable {
    if (iconResource == 0) return loadIcon(packageManager)
    return try {
        val resources = packageManager.getResourcesForApplication(activityInfo.applicationInfo)
        val legacyDensity = iconDensity(carDensity, size.value / LEGACY_ICON_SIZE_DP)
        when (val icon = resources.getDrawableForDensity(iconResource, legacyDensity, null)) {
            null -> loadIcon(packageManager)
            is AdaptiveIconDrawable -> {
                val layerScale = size.value * adaptiveIconLayerScale() / ADAPTIVE_ICON_LAYER_SIZE_DP
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

private fun adaptiveIconLayerScale(): Float = 1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction()

private fun iconDensity(carDensity: Int, scale: Float): Int = ceil(carDensity * scale).toInt()
