package io.github.octoclonius.libreauto.car.quicksettings

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.CarUi
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnectionState

internal class QuickSettingsWindow(context: Context, display: Display, private val carUi: CarUi) :
    ComponentDialog(
        context
            .createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null),
        R.style.Theme_LibreAuto_Car_Rail,
    ) {

    init {
        window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    val rail by carUi.session.displays.rail.state.collectAsStateWithLifecycle()
                    val connection by carUi.connection.collectAsStateWithLifecycle()
                    val settings by
                        remember { carUi.phoneSettings.settings }.collectAsStateWithLifecycle(null)
                    CarTheme {
                        QuickSettingsPanel(
                            settings = settings,
                            railSide = rail?.side,
                            isDriverOnRight =
                                (connection as? CarConnectionState.Connected)?.isDriverOnRight ==
                                    true,
                            onToggle = carUi.phoneSettings::set,
                            onClose = { carUi.quickSettings.close() },
                            onShowPage = ::showPage,
                        )
                    }
                }
            }
        )
        window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFormat(PixelFormat.TRANSLUCENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun showPage(page: PendingIntent) {
        carUi.quickSettings.close()
        carUi.session.displays.car.show(page)
    }
}
