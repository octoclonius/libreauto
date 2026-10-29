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
    private val exitAccessoryMode = 6
    private val startAudio = 8

    @Test
    fun onlyShizukuMayDestroy() {
        assertTrue(isCallerAllowed(destroy, serviceUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(destroy, appUid, appUid, serviceUid))
        assertFalse(isCallerAllowed(destroy, otherUid, appUid, serviceUid))
    }

    @Test
    fun onlyLibreAutoMayMakeTheOtherCalls() {
        for (code in listOf(getUid, createDisplay, exitAccessoryMode, startAudio)) {
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

    @Test
    fun onlyLibreAutoMayCallASession() {
        for (code in listOf(1, 2, 3, destroy)) {
            assertTrue(isAppCallAllowed(code, appUid, appUid, serviceUid))
            assertFalse(isAppCallAllowed(code, serviceUid, appUid, serviceUid))
            assertFalse(isAppCallAllowed(code, otherUid, appUid, serviceUid))
        }
    }

    @Test
    fun shizukuAndLibreAutoMayAskForASessionsInterface() {
        assertTrue(isAppCallAllowed(interfaceDescriptor, serviceUid, appUid, serviceUid))
        assertTrue(isAppCallAllowed(interfaceDescriptor, appUid, appUid, serviceUid))
        assertFalse(isAppCallAllowed(interfaceDescriptor, otherUid, appUid, serviceUid))
    }
}
