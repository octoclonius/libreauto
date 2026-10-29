package io.github.octoclonius.libreauto.phone.certificate

import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository
import java.time.Instant

data class CertificateProviderUiState(
    val selection: Selection = Selection.Loading,
    val picker: Picker? = null,
) {
    sealed interface Selection {
        data object Loading : Selection

        data object None : Selection

        data class Selected(
            val choice: CertificateRepository.Choice,
            val certificate: CertificateState,
        ) : Selection
    }

    sealed interface CertificateState {
        data object Loading : CertificateState

        data class Success(val notBefore: Instant, val notAfter: Instant) : CertificateState

        data class Error(val message: String) : CertificateState
    }

    sealed interface Picker {
        data object Loading : Picker

        data class Success(val options: List<CertificateRepository.Option>) : Picker
    }
}
