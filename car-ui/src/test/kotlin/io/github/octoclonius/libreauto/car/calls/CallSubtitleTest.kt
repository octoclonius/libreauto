package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.CallStatus
import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.CallerNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CallSubtitleTest {

    private val number = CallerNumber("6502530000", "(650) 253-0000", "US")
    private val connectTimeMillis = 1_700_000_000_000L

    @Test
    fun aContactShowsTheNumbersLabelAndTheNumber() {
        assertEquals(
            CallSubtitle.Number("(650) 253-0000", "Mobile"),
            subtitle(Caller.Contact("Alex Rivera", number), numberLabel = "Mobile"),
        )
    }

    @Test
    fun aContactWithoutALabelShowsTheNumber() {
        assertEquals(
            CallSubtitle.Number("(650) 253-0000", null),
            subtitle(Caller.Contact("Alex Rivera", number), numberLabel = null),
        )
    }

    @Test
    fun aNameTheNetworkSentShowsTheNumberWithoutALabel() {
        assertEquals(
            CallSubtitle.Number("(650) 253-0000", null),
            subtitle(Caller.Named("MOBILE CALLER", number), numberLabel = "Mobile"),
        )
    }

    @Test
    fun aNameWithoutANumberShowsNothing() {
        assertNull(subtitle(Caller.Named("MOBILE CALLER", null)))
        assertNull(subtitle(Caller.Contact("Alex Rivera", null)))
    }

    @Test
    fun aNumberShowsItsLocation() {
        assertEquals(
            CallSubtitle.Location("Mountain View, CA"),
            subtitle(Caller.Number(number), location = "Mountain View, CA"),
        )
    }

    @Test
    fun aNumberWithoutALocationShowsNothing() {
        assertNull(subtitle(Caller.Number(number), location = null))
        assertNull(subtitle(Caller.Number(number), location = ""))
    }

    @Test
    fun aHiddenNumberShowsNothing() {
        assertNull(subtitle(Caller.Private, location = "Mountain View, CA"))
        assertNull(subtitle(Caller.Payphone, location = "Mountain View, CA"))
        assertNull(subtitle(Caller.Unknown, location = "Mountain View, CA"))
    }

    @Test
    fun anAppCallShowsTheAppsName() {
        assertEquals(
            CallSubtitle.App("Caller"),
            subtitle(
                Caller.Contact("Alex Rivera", number),
                isAppCall = true,
                appName = "Caller",
                numberLabel = "Mobile",
            ),
        )
        assertEquals(
            CallSubtitle.App("Caller"),
            subtitle(
                Caller.Number(number),
                isAppCall = true,
                appName = "Caller",
                location = "Mountain View, CA",
            ),
        )
    }

    @Test
    fun anAppCallFromAnUnknownAppShowsNothing() {
        assertNull(subtitle(Caller.Number(number), isAppCall = true, appName = null))
    }

    @Test
    fun onlyTheSubtitleShownIsLookedUp() {
        val lookedUp = mutableListOf<String>()
        callSubtitle(
            CallStatus.Ringing,
            0,
            null,
            Caller.Named("MOBILE CALLER", number),
            isAppCall = false,
            appName = null,
            numberLabel = {
                lookedUp += "label"
                null
            },
            location = {
                lookedUp += "location"
                null
            },
        )
        callSubtitle(
            CallStatus.Ringing,
            0,
            null,
            Caller.Number(number),
            isAppCall = true,
            appName = "Caller",
            numberLabel = {
                lookedUp += "label"
                null
            },
            location = {
                lookedUp += "location"
                null
            },
        )
        assertEquals(emptyList<String>(), lookedUp)
    }

    @Test
    fun aDialingCallSaysDialing() {
        assertEquals(CallSubtitle.Dialing, status(CallStatus.Dialing))
    }

    @Test
    fun anActiveCallCountsFromWhenItConnected() {
        assertEquals(CallSubtitle.Ongoing(connectTimeMillis), status(CallStatus.Active))
    }

    @Test
    fun aHeldCallSaysOnHold() {
        assertEquals(CallSubtitle.OnHold, status(CallStatus.Holding))
    }

    @Test
    fun anEndingCallSaysHangingUp() {
        assertEquals(CallSubtitle.HangingUp, status(CallStatus.Disconnecting))
    }

    @Test
    fun anEndedCallShowsTheReasonItWasGiven() {
        assertEquals(
            CallSubtitle.Ended("Busy"),
            callSubtitle(
                CallStatus.Disconnected,
                connectTimeMillis,
                "Busy",
                Caller.Contact("Alex Rivera", number),
                isAppCall = false,
                appName = null,
                numberLabel = { "Mobile" },
                location = { "Mountain View, CA" },
            ),
        )
        assertEquals(CallSubtitle.Ended(null), status(CallStatus.Disconnected))
    }

    @Test
    fun aCallThatIsNotRingingLooksNothingUp() {
        val lookedUp = mutableListOf<String>()
        for (status in CallStatus.entries - CallStatus.Ringing) {
            callSubtitle(
                status,
                connectTimeMillis,
                null,
                Caller.Contact("Alex Rivera", number),
                isAppCall = false,
                appName = "Caller",
                numberLabel = {
                    lookedUp += "label"
                    null
                },
                location = {
                    lookedUp += "location"
                    null
                },
            )
        }
        assertEquals(emptyList<String>(), lookedUp)
    }

    @Test
    fun elapsedTimeCountsWholeSeconds() {
        assertEquals(0, elapsedSeconds(connectTimeMillis, connectTimeMillis + 999))
        assertEquals(42, elapsedSeconds(connectTimeMillis, connectTimeMillis + 42_000))
    }

    @Test
    fun elapsedTimeNeverGoesNegative() {
        assertEquals(0, elapsedSeconds(connectTimeMillis, connectTimeMillis - 5_000))
    }

    private fun status(status: CallStatus): CallSubtitle? =
        callSubtitle(
            status,
            connectTimeMillis,
            null,
            Caller.Contact("Alex Rivera", number),
            isAppCall = true,
            appName = "Caller",
            numberLabel = { "Mobile" },
            location = { "Mountain View, CA" },
        )

    private fun subtitle(
        caller: Caller,
        isAppCall: Boolean = false,
        appName: String? = null,
        numberLabel: String? = null,
        location: String? = null,
    ): CallSubtitle? =
        callSubtitle(
            CallStatus.Ringing,
            0,
            null,
            caller,
            isAppCall,
            appName,
            { numberLabel },
            { location },
        )
}
