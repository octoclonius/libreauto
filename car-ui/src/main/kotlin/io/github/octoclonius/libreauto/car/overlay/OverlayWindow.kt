package io.github.octoclonius.libreauto.car.overlay

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.designsystem.theme.CarTheme
import io.github.octoclonius.libreauto.car.notifications.HeadsUp
import io.github.octoclonius.libreauto.car.notifications.NotificationList
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsPanel
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository

internal class OverlayWindow(
    context: Context,
    display: Display,
    private val session: CarSession,
    private val quickSettings: QuickSettingsState,
    private val phoneSettings: PhoneSettingsRepository,
    private val notifications: NotificationsState,
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
                    val isOpen by quickSettings.isOpen.collectAsStateWithLifecycle()
                    val headsUp by notifications.headsUp.collectAsStateWithLifecycle()
                    val list by notifications.notifications.collectAsStateWithLifecycle()
                    var headsUpArea by remember { mutableStateOf(Rect()) }
                    LaunchedEffect(isOpen, headsUpArea) {
                        session.displays.overlay.setTouchArea(headsUpArea.takeUnless { isOpen })
                    }
                    CarTheme {
                        HeadsUp(
                            headsUp = headsUp.takeUnless { isOpen },
                            onClick = notifications::openHeadsUp,
                            onHide = notifications::hideHeadsUp,
                            onDismiss = notifications::dismiss,
                            onHold = notifications::holdHeadsUp,
                            onOnScreen = notifications::onHeadsUpOnScreen,
                            onBounds = { bounds ->
                                headsUpArea = Rect().also { bounds?.toAndroidRectF()?.roundOut(it) }
                            },
                        )
                        if (isOpen) {
                            QuickSettingsPanel(
                                settings = settings,
                                railSide = rail?.side,
                                isDriverOnRight = session.car.isDriverOnRight,
                                onToggle = phoneSettings::set,
                                onClose = { quickSettings.close() },
                                onShowPage = ::showPage,
                                hasNotifications = list.isNotEmpty(),
                            ) { modifier ->
                                NotificationList(
                                    list,
                                    onClick = notifications::open,
                                    onDismiss = notifications::dismiss,
                                    modifier = modifier,
                                )
                            }
                        }
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
        quickSettings.close()
        session.displays.car.show(page)
    }
}
