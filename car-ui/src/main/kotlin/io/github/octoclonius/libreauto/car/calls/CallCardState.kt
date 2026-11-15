package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.calls.PhoneCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class CallCardState(
    private val repository: CallRepository,
    private val popUpCall: StateFlow<PhoneCall?>,
    private val uiStates: CallUiStates,
) {

    private val _card = MutableStateFlow<CallUiState?>(null)
    val card: StateFlow<CallUiState?> = _card.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            uiStates
                .of(combine(repository.calls, popUpCall, ::listedCall).distinctUntilChanged())
                .collect { _card.value = it }
        }
    }

    fun answer(id: Int) {
        repository.answer(id)
    }

    fun decline(id: Int) {
        repository.reject(id)
    }

    fun hangUp(id: Int) {
        repository.disconnect(id)
    }
}
