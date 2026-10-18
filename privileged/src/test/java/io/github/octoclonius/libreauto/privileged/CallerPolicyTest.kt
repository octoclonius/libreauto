package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallerPolicyTest {

    private val appUid = 10_123
    private val serviceUid = 2000
    private val otherUid = 10_456

    private val destroy = 16777115
    private val interfaceDescriptor = 0x5f4e5446
    private val getUid = 2
    private val createDisplay = 3
    private val releaseDisplay = 4

    @Test
    fun onlyShizukuMayDestroy() {
        assertTrue(isCallerAllowed(destroy, serviceUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(destroy, appUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(destroy, otherUid, appUid, serviceUid))
    }

    @Test
    fun onlyLibreAutoMayMakeTheOtherCalls() {
        for (code in listOf(getUid, createDisplay, releaseDisplay)) {
            assertTrue(isCallerAllowed(code, appUid, appUid, serviceUid))
            assertFalse(isCallerAllowed(code, serviceUid, appUid, serviceUid))
            assertFalse(isCallerAllowed(code, otherUid, appUid, serviceUid))
        }
    }

    @Test
    fun shizukuAndLibreAutoMayAskForTheInterface() {
        assertTrue(isCallerAllowed(interfaceDescriptor, serviceUid, appUid, serviceUid))
        assertTrue(isCallerAllowed(interfaceDescriptor, appUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(interfaceDescriptor, otherUid, appUid, serviceUid))
    }

    @Test
    fun unknownCallsAreLibreAutosOnly() {
        assertTrue(isCallerAllowed(99, appUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(99, serviceUid, appUid, serviceUid))
    }
}
