package io.github.octoclonius.libreauto.car.calls

import io.github.octoclonius.libreauto.projection.calls.Caller
import io.github.octoclonius.libreauto.projection.calls.CallerNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CallSubtitleTest {

    private val number = CallerNumber("6502530000", "(650) 253-0000", "US")

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

    private fun subtitle(
        caller: Caller,
        isAppCall: Boolean = false,
        appName: String? = null,
        numberLabel: String? = null,
        location: String? = null,
    ): CallSubtitle? = callSubtitle(caller, isAppCall, appName, { numberLabel }, { location })
}
