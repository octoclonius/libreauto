package io.github.octoclonius.libreauto.projection.calls

enum class CallStatus {
    Ringing,
    Dialing,
    Active,
    Holding,
}

data class PhoneCall(val id: Int, val status: CallStatus)
