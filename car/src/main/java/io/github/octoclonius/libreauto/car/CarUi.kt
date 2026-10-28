package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.car.home.HomeNavigation
import io.github.octoclonius.libreauto.car.home.HomeState
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsHost
import io.github.octoclonius.libreauto.car.quicksettings.QuickSettingsState
import io.github.octoclonius.libreauto.car.rail.RailHost
import io.github.octoclonius.libreauto.car.rail.RailOrderRepository
import io.github.octoclonius.libreauto.car.rail.RailState
import io.github.octoclonius.libreauto.session.CarSession
import io.github.octoclonius.libreauto.session.input.NavigationButton
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CarUi
internal constructor(
    context: Context,
    val session: CarSession,
    internal val phoneSettings: PhoneSettingsRepository,
    internal val railOrder: RailOrderRepository,
) : Closeable {

    internal val home = HomeState()
    internal val rail = RailState()
    internal val quickSettings = QuickSettingsState()
    internal val navigation = HomeNavigation(session, home, rail, quickSettings)

    private val scope = MainScope()
    private val railHost = RailHost(context, this)
    private val quickSettingsHost = QuickSettingsHost(context, this)

    internal fun start() {
        railHost.start(scope)
        quickSettingsHost.start(scope)
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
    }
}
