package io.github.octoclonius.libreauto.car.touch

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val FADE_MILLIS = 250
private const val FINGER_ALPHA = 0.45f
private val FINGER_RADIUS = 15.dp
private val FINGER_COLOR = Color.White

@Stable
class TouchIndicatorState {
    internal val fingers = mutableStateListOf<Finger>()

    fun onTouch(event: MotionEvent) {
        val action = event.actionMasked
        val liftedId =
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                event.getPointerId(event.actionIndex)
            } else {
                null
            }
        val ids = List(event.pointerCount, event::getPointerId)
        if (action != MotionEvent.ACTION_CANCEL) {
            for (index in ids.indices) {
                val position = Offset(event.getX(index), event.getY(index))
                val finger = fingers.firstOrNull { it.id == ids[index] && it.isDown }
                if (finger == null) {
                    fingers += Finger(ids[index], position)
                } else {
                    finger.position = position
                }
            }
        }
        for (finger in fingers) {
            val isLifted =
                action == MotionEvent.ACTION_CANCEL || finger.id == liftedId || finger.id !in ids
            if (finger.isDown && isLifted) finger.isDown = false
        }
    }
}

@Stable
internal class Finger(val id: Int, position: Offset) {
    var position by mutableStateOf(position)
    var isDown by mutableStateOf(true)
    val alpha = Animatable(1f)
}

@Composable
fun TouchIndicator(state: TouchIndicatorState, modifier: Modifier = Modifier) {
    for (finger in state.fingers) {
        key(finger) {
            LaunchedEffect(finger.isDown) {
                if (!finger.isDown) {
                    finger.alpha.animateTo(0f, tween(FADE_MILLIS))
                    state.fingers.remove(finger)
                }
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        for (finger in state.fingers) {
            drawCircle(
                color = FINGER_COLOR,
                radius = FINGER_RADIUS.toPx(),
                center = finger.position,
                alpha = FINGER_ALPHA * finger.alpha.value,
            )
        }
    }
}
