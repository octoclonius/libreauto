package io.github.octoclonius.libreauto.car.calls

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.apps.loadCarAppIcon
import io.github.octoclonius.libreauto.car.overlay.isTouchAccepted
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.calls.CallingApp
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import io.github.octoclonius.libreauto.projection.display.CarDisplay
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class IncomingCallUiState(
    val call: PhoneCall,
    val subtitle: CallSubtitle?,
    val appIcon: Drawable?,
    val photo: ImageBitmap?,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class IncomingCallState(
    context: Context,
    private val repository: CallRepository,
    private val quickSettings: QuickSettingsState,
    private val carDisplay: CarDisplay,
) {

    private companion object {
        const val TAG = "IncomingCallState"
    }

    private class CallApp(val label: String, val icon: Drawable)

    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val resolver = context.contentResolver
    private val callerLookup = CallerLookup(context)

    private val hiddenIds = MutableStateFlow<Set<Int>>(emptySet())
    private val call = MutableStateFlow<PhoneCall?>(null)
    private var shownAt = 0L

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingCallUiState?>(null)
    val incoming: StateFlow<IncomingCallUiState?> = _incoming.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.calls, hiddenIds, ::incomingCall).collect { next ->
                if (next != null && next.id != call.value?.id) {
                    shownAt = SystemClock.elapsedRealtime()
                }
                call.value = next
                _isShown.value = next != null
            }
        }
        scope.launch {
            combine(call, carDensities(carDisplay)) { shown, carDensity ->
                    shown?.let { it to carDensity }
                }
                .mapLatest { shown ->
                    shown?.let { (call, carDensity) -> uiState(call, carDensity) }
                }
                .flowOn(Dispatchers.IO)
                .collect { _incoming.value = it }
        }
        scope.launch { quickSettings.isOpen.collect { if (it) hide() } }
    }

    fun answer(id: Int) {
        if (isTouchAccepted(shownAt, SystemClock.elapsedRealtime())) repository.answer(id)
    }

    fun decline(id: Int) {
        if (isTouchAccepted(shownAt, SystemClock.elapsedRealtime())) repository.reject(id)
    }

    fun hide() {
        val shown = call.value ?: return
        hiddenIds.update { it + shown.id }
    }

    private fun uiState(call: PhoneCall, carDensity: Int): IncomingCallUiState {
        val app = call.app?.let { loadApp(it, carDensity) }
        return IncomingCallUiState(
            call,
            callSubtitle(
                call.caller,
                call.app?.isSelfManaged == true,
                app?.label,
                callerLookup::numberLabel,
                callerLookup::location,
            ),
            app?.icon,
            call.photo?.let { loadPhoto(it, carDensity) },
        )
    }

    private fun loadApp(app: CallingApp, carDensity: Int): CallApp? {
        val info =
            try {
                launcherApps.getApplicationInfo(app.packageName, 0, app.user)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "The calling app ${app.packageName} isn't installed", e)
                return null
            }
        return CallApp(
            info.loadLabel(packageManager).toString(),
            loadCarAppIcon(packageManager, info, app.user, carDensity, CALLER_AVATAR_SIZE),
        )
    }

    private fun loadPhoto(uri: Uri, carDensity: Int): ImageBitmap? {
        val size = CALLER_AVATAR_SIZE.value * carDensity / DisplayMetrics.DENSITY_DEFAULT
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _
                    ->
                    val scale = size / minOf(info.size.width, info.size.height)
                    if (scale < 1) {
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt(),
                            (info.size.height * scale).roundToInt(),
                        )
                    }
                }
                .asImageBitmap()
        } catch (e: IOException) {
            Log.i(TAG, "No contact photo for the caller", e)
            null
        } catch (e: SecurityException) {
            Log.i(TAG, "LibreAuto may not read the caller's contact photo", e)
            null
        }
    }
}
