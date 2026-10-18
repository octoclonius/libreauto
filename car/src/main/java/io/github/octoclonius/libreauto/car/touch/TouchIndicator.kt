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
import io.github.octoclonius.libreauto.session.input.Touch

private const val FADE_MILLIS = 250
private const val FINGER_ALPHA = 0.45f
private val FINGER_RADIUS = 15.dp
private val FINGER_COLOR = Color.White

@Stable
class TouchIndicatorState {
    internal val fingers = mutableStateListOf<Finger>()

    fun onTouch(touch: Touch) {
        val liftedId =
            if (
                touch.action == MotionEvent.ACTION_UP ||
                    touch.action == MotionEvent.ACTION_POINTER_UP
            ) {
                touch.pointers[touch.actionIndex].id
            } else {
                null
            }
        for (pointer in touch.pointers) {
            val position = Offset(pointer.x.toFloat(), pointer.y.toFloat())
            val finger = fingers.firstOrNull { it.id == pointer.id && it.isDown }
            if (finger == null) {
                fingers += Finger(pointer.id, position)
            } else {
                finger.position = position
            }
        }
        for (finger in fingers) {
            val isLifted = finger.id == liftedId || touch.pointers.none { it.id == finger.id }
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
