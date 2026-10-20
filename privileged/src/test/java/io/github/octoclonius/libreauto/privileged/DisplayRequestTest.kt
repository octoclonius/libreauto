package io.github.octoclonius.libreauto.privileged

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DisplayRequestTest {

    private val app = "io.github.octoclonius.libreauto"

    @Test
    fun aDisplayWithLibreAutosHomeIsAccepted() {
        assertNull(displayRequestError(1280, 720, 160, app, app))
    }

    @Test
    fun aHomeFromAnotherPackageIsRefused() {
        assertNotNull(displayRequestError(1280, 720, 160, "com.example.launcher", app))
        assertNotNull(displayRequestError(1280, 720, 160, "$app.other", app))
    }

    @Test
    fun anEmptySizeIsRefused() {
        assertNotNull(displayRequestError(0, 720, 160, app, app))
        assertNotNull(displayRequestError(1280, -1, 160, app, app))
    }

    @Test
    fun aDensityThatIsntPositiveIsRefused() {
        assertNotNull(displayRequestError(1280, 720, 0, app, app))
        assertNotNull(displayRequestError(1280, 720, -160, app, app))
    }
}
