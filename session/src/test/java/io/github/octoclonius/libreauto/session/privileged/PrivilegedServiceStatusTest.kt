package io.github.octoclonius.libreauto.session.privileged

import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceState.Failed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivilegedServiceStatusTest {

    private val stopped = ShizukuStatus()
    private val ready =
        ShizukuStatus(isRunning = true, version = MIN_SHIZUKU_VERSION, isPermissionGranted = true)
    private val ungranted = ready.copy(isPermissionGranted = false)
    private val start = PrivilegedServiceStatus()

    @Test
    fun withoutShizukuItIsStopped() {
        assertEquals(PrivilegedServiceState.Stopped, start.state)
        assertFalse(start.shouldBind)
        assertEquals(PrivilegedServiceState.Stopped, start.withShizuku(stopped).state)
    }

    @Test
    fun anOlderShizukuFailsWithItsVersion() {
        val status = start.withShizuku(ready.copy(version = MIN_SHIZUKU_VERSION - 1))
        assertEquals(
            Failed(Failed.Reason.ShizukuOutdated(MIN_SHIZUKU_VERSION - 1)),
            status.state,
        )
        assertFalse(status.shouldBind)
    }

    @Test
    fun aShizukuThatCantBeAskedFailsWithItsError() {
        val status = start.withShizuku(ShizukuStatus(isRunning = true, error = "x"))
        assertEquals(Failed(Failed.Reason.Error("x")), status.state)
        assertFalse(status.shouldBind)
    }

    @Test
    fun withoutPermissionItIsNeeded() {
        val status = start.withShizuku(ungranted)
        assertEquals(PrivilegedServiceState.PermissionNeeded, status.state)
        assertFalse(status.shouldBind)
    }

    @Test
    fun aPermissionDeniedForGoodFails() {
        val status = start.withShizuku(ungranted.copy(isPermissionDenied = true))
        assertEquals(Failed(Failed.Reason.PermissionDenied), status.state)
        assertFalse(status.shouldBind)
        assertEquals(status, status.retried())
    }

    @Test
    fun aOneTimeDenialAsksAgain() {
        val status =
            start.withShizuku(ungranted.copy(isPermissionDenied = true)).withShizuku(ungranted)
        assertEquals(PrivilegedServiceState.PermissionNeeded, status.state)
    }

    @Test
    fun aGrantedPermissionIgnoresADenial() {
        val status = start.withShizuku(ready.copy(isPermissionDenied = true))
        assertEquals(PrivilegedServiceState.Connecting, status.state)
        assertTrue(status.shouldBind)
    }

    @Test
    fun aReadyShizukuBindsOnce() {
        val binding = start.withShizuku(ready).bindingStarted()
        assertEquals(PrivilegedServiceState.Connecting, binding.state)
        assertFalse(binding.shouldBind)
        assertFalse(binding.withShizuku(ready).shouldBind)
        assertEquals(binding, binding.bindingStarted())
    }

    @Test
    fun aConnectedServiceIsRunning() {
        val status = start.withShizuku(ready).bindingStarted().connected()
        assertEquals(PrivilegedServiceState.Running, status.state)
        assertEquals(PrivilegedServiceState.Running, status.withShizuku(ready).state)
    }

    @Test
    fun aConnectionWithoutABindingIsIgnored() {
        val status = start.withShizuku(ready).connected()
        assertEquals(PrivilegedServiceState.Connecting, status.state)
        assertTrue(status.shouldBind)
    }

    @Test
    fun aFailedBindingFailsWithItsReason() {
        val status = start.withShizuku(ready).bindingStarted().failed(Failed.Reason.Error("x"))
        assertEquals(Failed(Failed.Reason.Error("x")), status.state)
        assertFalse(status.shouldBind)
    }

    @Test
    fun aServiceThatStopsFailsWithoutRebinding() {
        val status =
            start
                .withShizuku(ready)
                .bindingStarted()
                .connected()
                .failed(Failed.Reason.ServiceStopped)
        assertEquals(Failed(Failed.Reason.ServiceStopped), status.state)
        assertFalse(status.shouldBind)
        assertEquals(Failed(Failed.Reason.ServiceStopped), status.withShizuku(ready).state)
        assertFalse(status.withShizuku(ready).shouldBind)
    }

    @Test
    fun retryingAFailureBindsAgain() {
        val status =
            start
                .withShizuku(ready)
                .bindingStarted()
                .failed(Failed.Reason.ServiceStopped)
                .retried()
                .withShizuku(ready)
        assertEquals(PrivilegedServiceState.Connecting, status.state)
        assertTrue(status.shouldBind)
    }

    @Test
    fun retryingWithoutAFailureChangesNothing() {
        val running = start.withShizuku(ready).bindingStarted().connected()
        assertEquals(running, running.retried())
        val binding = start.withShizuku(ready).bindingStarted()
        assertEquals(binding, binding.retried())
    }

    @Test
    fun shizukuStoppingDropsTheServiceAndStartingAgainBinds() {
        val stoppedAgain =
            start.withShizuku(ready).bindingStarted().connected().withShizuku(stopped)
        assertEquals(PrivilegedServiceState.Stopped, stoppedAgain.state)
        val restarted = stoppedAgain.withShizuku(ready)
        assertEquals(PrivilegedServiceState.Connecting, restarted.state)
        assertTrue(restarted.shouldBind)
    }

    @Test
    fun shizukuRestartingClearsAFailure() {
        val restarted =
            start
                .withShizuku(ready)
                .bindingStarted()
                .failed(Failed.Reason.ServiceStopped)
                .withShizuku(stopped)
                .withShizuku(ready)
        assertEquals(PrivilegedServiceState.Connecting, restarted.state)
        assertTrue(restarted.shouldBind)
    }

    @Test
    fun aFailureAfterShizukuStoppedIsIgnored() {
        val status =
            start
                .withShizuku(ready)
                .bindingStarted()
                .connected()
                .withShizuku(stopped)
                .failed(Failed.Reason.ServiceStopped)
        assertEquals(PrivilegedServiceState.Stopped, status.state)
        assertTrue(status.withShizuku(ready).shouldBind)
    }

    @Test
    fun losingThePermissionDropsTheService() {
        val revoked = start.withShizuku(ready).bindingStarted().connected().withShizuku(ungranted)
        assertEquals(PrivilegedServiceState.PermissionNeeded, revoked.state)
        assertTrue(revoked.withShizuku(ready).shouldBind)
    }
}
