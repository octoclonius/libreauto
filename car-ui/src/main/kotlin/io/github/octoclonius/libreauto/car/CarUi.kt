package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.calls.CallCardState
import io.github.octoclonius.libreauto.car.calls.CallScreenHost
import io.github.octoclonius.libreauto.car.calls.CallScreenState
import io.github.octoclonius.libreauto.car.calls.CallUiStates
import io.github.octoclonius.libreauto.car.calls.IncomingCallState
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.notifications.NotificationsState
import io.github.octoclonius.libreauto.car.overlay.PopUpHost
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsHost
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailHost
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.projection.CarSession
import io.github.octoclonius.libreauto.projection.calls.CallRepository
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
    callRepository: CallRepository,
) : Closeable {

    internal val home = HomeState()
    internal val rail = RailState()
    internal val quickSettings = QuickSettingsState()
    private val callUiStates = CallUiStates(context, session.displays.car)
    private val callScreen = CallScreenState(callRepository, callUiStates)
    internal val navigation = CarNavigation(session, home, rail, quickSettings, callScreen)

    private val scope = MainScope()
    private val railHost =
        RailHost(
            context,
            session,
            rail,
            railOrder,
            navigation,
            callRepository.calls,
            callScreen.isShown,
        )
    private val incomingCall = IncomingCallState(callRepository, callUiStates)
    private val callCard = CallCardState(callRepository, incomingCall.call, callUiStates)
    private val notifications =
        NotificationsState(
            context,
            notificationRepository,
            quickSettings,
            session.displays.car,
            incomingCall.isShown,
        )
    private val quickSettingsHost =
        QuickSettingsHost(
            context,
            session,
            quickSettings,
            phoneSettings,
            notifications,
            callCard,
            navigation::onCallCardClick,
        )
    private val popUpHost = PopUpHost(context, session, notifications, incomingCall)
    private val callScreenHost = CallScreenHost(context, session, callScreen)

    internal fun start() {
        railHost.start(scope)
        notifications.start(scope)
        quickSettingsHost.start(scope)
        incomingCall.start(scope)
        callCard.start(scope)
        callScreen.start(scope)
        callScreenHost.start(scope)
        popUpHost.start(scope)
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
        quickSettingsHost.close()
        popUpHost.close()
        callScreenHost.close()
    }
}
