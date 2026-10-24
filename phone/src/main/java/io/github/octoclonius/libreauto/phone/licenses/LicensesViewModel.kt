package io.github.octoclonius.libreauto.phone.licenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LicensesUiState {
    data object Loading : LicensesUiState

    data class Success(val libraries: List<Library>) : LicensesUiState
}

class LicensesViewModel(context: Context) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LicensesViewModel(checkNotNull(this[APPLICATION_KEY])) }
        }
    }

    private val _uiState = MutableStateFlow<LicensesUiState>(LicensesUiState.Loading)
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val libraries =
                withContext(Dispatchers.IO) {
                    Libs.Builder().withContext(context).build().libraries
                }
            _uiState.value = LicensesUiState.Success(libraries)
        }
    }
}
