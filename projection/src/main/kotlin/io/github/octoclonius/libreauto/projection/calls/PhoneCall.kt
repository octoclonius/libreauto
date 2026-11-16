package io.github.octoclonius.libreauto.projection.calls

import android.net.Uri
import android.os.UserHandle

enum class CallStatus {
    Ringing,
    Dialing,
    Active,
    Holding,
    Disconnecting,
    Disconnected;

    val isLive: Boolean
        get() = this != Disconnecting && this != Disconnected
}

data class CallerNumber(val number: String, val formatted: String, val countryIso: String)

sealed interface Caller {
    data class Contact(val name: String, val number: CallerNumber?) : Caller

    data class Named(val name: String, val number: CallerNumber?) : Caller

    data class Number(val number: CallerNumber) : Caller

    data class Voicemail(val name: String) : Caller

    data object Private : Caller

    data object Payphone : Caller

    data object Unknown : Caller
}

data class CallingApp(val packageName: String, val user: UserHandle, val isSelfManaged: Boolean)

data class PhoneCall(
    val id: Int,
    val status: CallStatus,
    val connectTimeMillis: Long,
    val caller: Caller,
    val photo: Uri?,
    val app: CallingApp?,
    val capabilities: Int,
    val isConference: Boolean,
    val participants: List<PhoneCall>,
    val postDialWait: String?,
    val disconnectLabel: String?,
)
