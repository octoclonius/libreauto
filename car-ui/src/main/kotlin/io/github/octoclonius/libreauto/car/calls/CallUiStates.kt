package io.github.octoclonius.libreauto.car.calls

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import io.github.octoclonius.libreauto.car.apps.carDensities
import io.github.octoclonius.libreauto.car.apps.loadCarAppIcon
import io.github.octoclonius.libreauto.projection.calls.CallingApp
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import io.github.octoclonius.libreauto.projection.display.CarDisplay
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

internal data class CallUiState(
    val call: PhoneCall,
    val subtitle: CallSubtitle?,
    val appIcon: Drawable?,
    val photo: ImageBitmap?,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class CallUiStates(context: Context, private val carDisplay: CarDisplay) {

    private companion object {
        const val TAG = "CallUiStates"
    }

    private class CallApp(val label: String, val icon: Drawable)

    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val resolver = context.contentResolver
    private val callerLookup = CallerLookup(context)

    fun of(calls: Flow<PhoneCall?>, avatarSize: Dp): Flow<CallUiState?> =
        combine(calls, carDensities(carDisplay)) { call, carDensity ->
                call?.let { it to carDensity }
            }
            .mapLatest { shown ->
                shown?.let { (call, carDensity) -> uiState(call, carDensity, avatarSize) }
            }
            .flowOn(Dispatchers.IO)

    private fun uiState(call: PhoneCall, carDensity: Int, avatarSize: Dp): CallUiState {
        val app = call.app?.let { loadApp(it, carDensity, avatarSize) }
        return CallUiState(
            call,
            callSubtitle(
                call.status,
                call.connectTimeMillis,
                call.disconnectLabel,
                call.caller,
                call.app?.isSelfManaged == true,
                app?.label,
                callerLookup::numberLabel,
                callerLookup::location,
            ),
            app?.icon,
            call.photo?.let { loadPhoto(it, carDensity, avatarSize) },
        )
    }

    private fun loadApp(app: CallingApp, carDensity: Int, size: Dp): CallApp? {
        val info =
            try {
                launcherApps.getApplicationInfo(app.packageName, 0, app.user)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "The calling app ${app.packageName} isn't installed", e)
                return null
            }
        return CallApp(
            info.loadLabel(packageManager).toString(),
            loadCarAppIcon(packageManager, info, app.user, carDensity, size),
        )
    }

    private fun loadPhoto(uri: Uri, carDensity: Int, avatarSize: Dp): ImageBitmap? {
        val size = avatarSize.value * carDensity / DisplayMetrics.DENSITY_DEFAULT
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
