package io.github.octoclonius.libreauto.car.quicksettings

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.designsystem.theme.CarTheme
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository

internal class QuickSettingsWindow(
    context: Context,
    display: Display,
    private val session: CarSession,
    private val state: QuickSettingsState,
    private val phoneSettings: PhoneSettingsRepository,
) :
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
                    val rail by session.displays.rail.state.collectAsStateWithLifecycle()
                    val settings by remember {
                        phoneSettings.settings
                    }
                        .collectAsStateWithLifecycle(null)
                    CarTheme {
                        QuickSettingsPanel(
                            settings = settings,
                            railSide = rail?.side,
                            isDriverOnRight = session.car.isDriverOnRight,
                            onToggle = phoneSettings::set,
                            onClose = { state.close() },
                            onShowPage = ::showPage,
                        )
                    }
                }
            }
        )
        window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFormat(PixelFormat.TRANSLUCENT)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun showPage(page: PendingIntent) {
        state.close()
        session.displays.car.show(page)
    }
}
