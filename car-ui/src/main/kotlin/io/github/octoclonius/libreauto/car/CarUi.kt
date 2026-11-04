package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.car.overlay.OverlayHost
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailHost
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.input.NavigationButton
import io.github.octoclonius.libreauto.projection.notifications.NotificationRepository
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CarUi
internal constructor(
    context: Context,
    val session: CarSession,
    phoneSettings: PhoneSettingsRepository,
    railOrder: RailOrderRepository,
    notificationRepository: NotificationRepository,
) : Closeable {

    internal val home = HomeState()
    internal val rail = RailState()
    internal val quickSettings = QuickSettingsState()
    internal val navigation = CarNavigation(session, home, rail, quickSettings)

    private val scope = MainScope()
    private val railHost = RailHost(context, session, rail, railOrder, navigation)
    private val notifications =
        NotificationsState(context, notificationRepository, quickSettings, session.displays.car)
    private val overlayHost =
        OverlayHost(context, session, quickSettings, phoneSettings, notifications)

    internal fun start() {
        railHost.start(scope)
        notifications.start(scope)
        overlayHost.start(scope)
        scope.launch {
            session.buttons.presses.collect {
                when (it) {
                    NavigationButton.Home -> navigation.onHomeButton()
                    NavigationButton.Back -> navigation.onBackButton()
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        railHost.close()
        overlayHost.close()
    }
}
