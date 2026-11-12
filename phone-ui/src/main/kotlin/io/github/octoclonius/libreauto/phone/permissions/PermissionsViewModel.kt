package io.github.octoclonius.libreauto.phone.permissions

import android.content.ComponentName
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.octoclonius.libreauto.projection.permissions.PermissionsRepository
import io.github.octoclonius.libreauto.projection.permissions.PermissionsState
import io.github.octoclonius.libreauto.projection.projectionContainer
import kotlinx.coroutines.flow.StateFlow

class PermissionsViewModel(private val permissions: PermissionsRepository) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PermissionsViewModel(
                    checkNotNull(this[APPLICATION_KEY]).projectionContainer.permissionsRepository
                )
            }
        }
    }

    val uiState: StateFlow<PermissionsState> = permissions.state

    val notificationListener: ComponentName = permissions.notificationListener

    fun refresh() {
        permissions.refresh()
    }
}
