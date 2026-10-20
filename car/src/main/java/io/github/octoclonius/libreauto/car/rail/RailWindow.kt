package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.octoclonius.libreauto.car.R
import io.github.octoclonius.libreauto.car.home.onAppsButton
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnection
import io.github.octoclonius.libreauto.session.CarConnectionState
import io.github.octoclonius.libreauto.session.CarStatusBar
import io.github.octoclonius.libreauto.session.video.RailDisplayState

internal class RailWindow(context: Context, display: Display, private val rail: RailDisplayState) :
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
        setContentView(ComposeView(context).apply { setContent { CarTheme { RailContent(rail) } } })
        window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
}

@Composable
private fun RailContent(rail: RailDisplayState) {
    val context = LocalContext.current
    val connection by CarConnection.state.collectAsStateWithLifecycle()
    val connected = connection as? CarConnectionState.Connected
    val insets = rail.contentInsets
    val padding =
        with(LocalDensity.current) {
            PaddingValues.Absolute(
                left = insets.left.toDp(),
                top = insets.top.toDp(),
                right = insets.right.toDp(),
                bottom = insets.bottom.toDp(),
            )
        }
    Rail(
        side = rail.side,
        isDriverOnRight = connected?.isDriverOnRight == true,
        carStatusBar =
            connected?.carStatusBar
                ?: CarStatusBar(
                    isClockShown = false,
                    isPhoneSignalShown = false,
                    isBatteryShown = false,
                ),
        contentPadding = padding,
        onAppsClick = { onAppsButton(context) },
        modifier = Modifier.fillMaxSize(),
    )
}
