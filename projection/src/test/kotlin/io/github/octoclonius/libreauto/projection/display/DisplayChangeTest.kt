package io.github.octoclonius.libreauto.projection.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayChangeTest {

    private class Service

    private val first = Service()
    private val second = Service()

    @Test
    fun noServiceMeansNoDisplay() {
        assertEquals(DisplayChange<Service>(null, null), displayChange(null, null))
    }

    @Test
    fun aRunningServiceCreatesTheDisplay() {
        assertEquals(DisplayChange(null, first), displayChange(null, first))
    }

    @Test
    fun theSameServiceKeepsItsDisplay() {
        assertEquals(
            DisplayChange<Service>(null, null),
            displayChange(OwnedDisplay(first, 3), first),
        )
    }

    @Test
    fun aStoppedServiceReleasesItsDisplay() {
        assertEquals(
            DisplayChange<Service>(OwnedDisplay(first, 3), null),
            displayChange(OwnedDisplay(first, 3), null),
        )
    }

    @Test
    fun aNewServiceReplacesTheOldOnesDisplay() {
        assertEquals(
            DisplayChange(OwnedDisplay(first, 3), second),
            displayChange(OwnedDisplay(first, 3), second),
        )
    }
}
