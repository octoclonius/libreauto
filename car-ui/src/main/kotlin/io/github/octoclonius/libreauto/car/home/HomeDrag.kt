package io.github.octoclonius.libreauto.car.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.octoclonius.libreauto.car.apps.PhoneApp

@Stable
class HomeDrag {
    var app by mutableStateOf<PhoneApp?>(null)
        private set

    var from by mutableStateOf<Int?>(null)
        private set

    var position by mutableStateOf(Offset.Zero)
        internal set

    var removeCenter by mutableStateOf<Offset?>(null)
    var removeRadius by mutableFloatStateOf(0f)
    val cells = mutableStateMapOf<Int, Rect>()

    val isDragging: Boolean
        get() = app != null

    fun lift(app: PhoneApp, from: Int?) {
        this.app = app
        this.from = from
    }

    internal fun targetCell(): Int? = cells.entries.firstOrNull { it.value.contains(position) }?.key

    internal fun isOverRemove(): Boolean {
        val center = removeCenter ?: return false
        return (position - center).getDistance() <= removeRadius
    }

    internal fun reset() {
        app = null
        from = null
    }
}

fun Modifier.homeDrag(
    drag: HomeDrag,
    onDrop: (app: PhoneApp, from: Int?, cell: Int?, isRemoved: Boolean) -> Unit,
): Modifier =
    pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            drag.position = down.position
            while (true) {
                val change =
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.id == down.id
                    } ?: break
                drag.position = change.position
                val app = drag.app
                if (app != null) change.consume()
                if (!change.pressed) {
                    if (app != null) onDrop(app, drag.from, drag.targetCell(), drag.isOverRemove())
                    break
                }
            }
            drag.reset()
        }
    }
