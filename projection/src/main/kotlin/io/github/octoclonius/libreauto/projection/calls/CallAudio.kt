package io.github.octoclonius.libreauto.projection.calls

import android.telecom.CallEndpoint

data class CallAudio(
    val isMuted: Boolean,
    val endpoint: CallEndpoint?,
    val endpoints: List<CallEndpoint>,
)
