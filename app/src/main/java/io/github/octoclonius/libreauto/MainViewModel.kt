package io.github.octoclonius.libreauto

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.octoclonius.libreauto.certificate.CertificateProvider
import io.github.octoclonius.libreauto.certificate.CertificateProviderUiState
import io.github.octoclonius.libreauto.certificate.CertificateProviderUiState.CertificateState
import io.github.octoclonius.libreauto.certificate.CertificateProviderUiState.Picker
import io.github.octoclonius.libreauto.certificate.CertificateProviderUiState.Selection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val certificateProvider: CertificateProviderUiState = CertificateProviderUiState(),
    val isRefreshing: Boolean = false,
)

class MainViewModel(private val certificateProvider: CertificateProvider) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(CertificateProvider(checkNotNull(this[APPLICATION_KEY]))) }
        }
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var certificateCheckJob: Job? = null
    private var pickerLoadJob: Job? = null

    private val foreground =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = checkCertificate()
        }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foreground)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        checkCertificate()
    }

    fun openCertificatePicker() {
        if (_uiState.value.certificateProvider.picker != null) return
        updateCertificateProvider { it.copy(picker = Picker.Loading) }
        pickerLoadJob =
            viewModelScope.launch {
                val options = withContext(Dispatchers.IO) { certificateProvider.options() }
                updateCertificateProvider { it.copy(picker = Picker.Success(options)) }
            }
    }

    fun closeCertificatePicker() {
        pickerLoadJob?.cancel()
        updateCertificateProvider { it.copy(picker = null) }
    }

    fun chooseCertificateProvider(choice: CertificateProvider.Choice?) {
        closeCertificatePicker()
        certificateProvider.select(choice)
        checkCertificate()
    }

    private fun checkCertificate() {
        certificateCheckJob?.cancel()
        certificateCheckJob =
            viewModelScope.launch {
                val choice = withContext(Dispatchers.IO) { certificateProvider.selection() }
                if (choice == null) {
                    updateSelection(Selection.None)
                } else {
                    val shown = _uiState.value.certificateProvider.selection
                    val certificate =
                        (shown as? Selection.Selected)?.takeIf { it.choice == choice }?.certificate
                    updateSelection(
                        Selection.Selected(choice, certificate ?: CertificateState.Loading)
                    )
                    val result = withContext(Dispatchers.IO) { certificateProvider.fetch(choice) }
                    val fetched =
                        when (result) {
                            is CertificateProvider.FetchResult.Success ->
                                CertificateState.Success(
                                    result.certificate.notBefore.toInstant(),
                                    result.certificate.notAfter.toInstant(),
                                )
                            is CertificateProvider.FetchResult.Error ->
                                CertificateState.Error(result.message)
                        }
                    updateSelection(Selection.Selected(choice, fetched))
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
    }

    private fun updateSelection(selection: Selection) {
        updateCertificateProvider { it.copy(selection = selection) }
    }

    private fun updateCertificateProvider(
        transform: (CertificateProviderUiState) -> CertificateProviderUiState
    ) {
        _uiState.update { it.copy(certificateProvider = transform(it.certificateProvider)) }
    }
}
