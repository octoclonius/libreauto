package io.github.octoclonius.libreauto.projection.display

class CarDisplays internal constructor(home: CarHome) {

    val car = CarDisplay(home)
    val rail = RailDisplay()
    val ongoingCallScreen = OverlayDisplay("ongoing call screen")
    val ringingCallScreen = OverlayDisplay("ringing call screen")
    val panel = OverlayDisplay("panel")
    val popUp = OverlayDisplay("pop-up")

    internal val overlays = listOf(ongoingCallScreen, ringingCallScreen, panel, popUp)

    private val lock = Any()
    private var shownOverlays: Set<OverlayDisplay> = emptySet()
    private var onShow: ((Set<Int>) -> Unit)? = null

    fun showOverlays(shown: Set<OverlayDisplay>) {
        synchronized(lock) {
            shownOverlays = shown
            onShow?.invoke(indices(shown))
        }
    }

    internal fun onOverlaysCreated(onShow: (Set<Int>) -> Unit) {
        synchronized(lock) {
            this.onShow = onShow
            onShow(indices(shownOverlays))
        }
    }

    internal fun onOverlaysReleased() {
        synchronized(lock) { onShow = null }
    }

    internal fun onOverlaysShown(shown: Set<Int>) {
        overlays.forEachIndexed { index, overlay -> overlay.isShown = index in shown }
    }

    private fun indices(shown: Set<OverlayDisplay>): Set<Int> =
        overlays.indices.filterTo(mutableSetOf()) { overlays[it] in shown }
}
