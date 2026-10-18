package io.github.octoclonius.libreauto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.octoclonius.libreauto.car.ui.theme.CarTheme
import io.github.octoclonius.libreauto.session.CarConnection
import io.github.octoclonius.libreauto.session.CarConnectionState

class CarPresentation(context: Context, display: Display) :
    Presentation(context, display), SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val decorView = checkNotNull(window).decorView
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    val state by CarConnection.state.collectAsStateWithLifecycle()
                    CarTheme {
                        CarScreen(carName = (state as? CarConnectionState.Connected)?.carName)
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onStop()
    }
}
