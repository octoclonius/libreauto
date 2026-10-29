package io.github.octoclonius.libreauto.phone

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.CertificateState
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.Picker
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.Selection
import io.github.octoclonius.libreauto.projection.CarConnectionState
import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceState
import io.github.octoclonius.libreauto.projection.projectionContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val car: CarConnectionState = CarConnectionState.Disconnected,
    val privilegedService: PrivilegedServiceState = PrivilegedServiceState.Stopped,
    val certificateProvider: CertificateProviderUiState = CertificateProviderUiState(),
    val isRefreshing: Boolean = false,
)

class MainViewModel(
    private val certificates: CertificateRepository,
    carConnectionState: StateFlow<CarConnectionState>,
    private val privilegedService: PrivilegedServiceConnection,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    application.projectionContainer.certificateRepository,
                    application.projectionContainer.carSessionHolder.state,
                    application.projectionContainer.privilegedServiceConnection,
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var certificateCheckJob: Job? = null
    private var pickerLoadJob: Job? = null

    private val foreground =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                privilegedService.refresh()
                checkCertificate()
            }
        }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
        viewModelScope.launch {
            carConnectionState.collect { car -> _uiState.update { it.copy(car = car) } }
        }
        viewModelScope.launch {
            privilegedService.state.collect { service ->
                _uiState.update { it.copy(privilegedService = service) }
            }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foreground)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        privilegedService.retry()
        checkCertificate()
    }

    fun requestShizukuPermission() {
        privilegedService.requestPermission()
    }

    fun openCertificatePicker() {
        if (_uiState.value.certificateProvider.picker != null) return
        updateCertificateProvider { it.copy(picker = Picker.Loading) }
        pickerLoadJob = viewModelScope.launch {
            val options = certificates.options()
            updateCertificateProvider { it.copy(picker = Picker.Success(options)) }
        }
    }

    fun closeCertificatePicker() {
        pickerLoadJob?.cancel()
        updateCertificateProvider { it.copy(picker = null) }
    }

    fun chooseCertificateProvider(choice: CertificateRepository.Choice?) {
        closeCertificatePicker()
        viewModelScope.launch {
            certificates.select(choice)
            checkCertificate()
        }
    }

    private fun checkCertificate() {
        certificateCheckJob?.cancel()
        certificateCheckJob = viewModelScope.launch {
            val choice = certificates.selection.first()
            if (choice == null) {
                updateSelection(Selection.None)
            } else {
                val shown = _uiState.value.certificateProvider.selection
                val certificate =
                    (shown as? Selection.Selected)?.takeIf { it.choice == choice }?.certificate
                updateSelection(Selection.Selected(choice, certificate ?: CertificateState.Loading))
                val result = certificates.fetch(choice)
                val fetched =
                    when (result) {
                        is CertificateRepository.FetchResult.Success ->
                            CertificateState.Success(
                                result.certificate.notBefore.toInstant(),
                                result.certificate.notAfter.toInstant(),
                            )
                        is CertificateRepository.FetchResult.Error ->
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
