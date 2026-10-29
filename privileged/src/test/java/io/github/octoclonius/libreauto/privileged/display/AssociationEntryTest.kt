package io.github.octoclonius.libreauto.privileged.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AssociationEntryTest {

    private val appStreaming = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
    private val watch = "android.app.role.COMPANION_DEVICE_WATCH"

    @Test
    fun noAssociationsHaveNoneOfOurs() {
        assertNull(ourAssociationId(emptyList(), appStreaming))
    }

    @Test
    fun anAppStreamingAssociationWithOurAddressIsOurs() {
        assertEquals(
            7,
            ourAssociationId(
                listOf(AssociationEntry(7, appStreaming, ASSOCIATION_ADDRESS)),
                appStreaming,
            ),
        )
    }

    @Test
    fun theAddressMatchesInAnyCase() {
        assertEquals(
            7,
            ourAssociationId(
                listOf(AssociationEntry(7, appStreaming, ASSOCIATION_ADDRESS.uppercase())),
                appStreaming,
            ),
        )
    }

    @Test
    fun anotherProfileOrAddressIsntOurs() {
        assertNull(
            ourAssociationId(
                listOf(
                    AssociationEntry(1, watch, ASSOCIATION_ADDRESS),
                    AssociationEntry(2, appStreaming, "02:00:00:00:00:02"),
                    AssociationEntry(3, appStreaming, null),
                    AssociationEntry(4, null, ASSOCIATION_ADDRESS),
                ),
                appStreaming,
            )
        )
    }

    @Test
    fun theOldestOfSeveralIsReused() {
        assertEquals(
            3,
            ourAssociationId(
                listOf(
                    AssociationEntry(5, appStreaming, ASSOCIATION_ADDRESS),
                    AssociationEntry(1, watch, ASSOCIATION_ADDRESS),
                    AssociationEntry(3, appStreaming, ASSOCIATION_ADDRESS),
                ),
                appStreaming,
            ),
        )
    }
}
