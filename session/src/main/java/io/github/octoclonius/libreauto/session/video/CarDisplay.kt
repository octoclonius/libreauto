package io.github.octoclonius.libreauto.session.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CarDisplay {
    private val _id = MutableStateFlow<Int?>(null)
    val id: StateFlow<Int?> = _id.asStateFlow()

    internal fun onCreated(id: Int) {
        _id.value = id
    }

    internal fun onReleased(id: Int) {
        _id.compareAndSet(id, null)
    }
}
