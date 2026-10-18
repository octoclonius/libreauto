package io.github.octoclonius.libreauto.privileged

import android.view.MotionEvent

internal const val TOUCHSCREEN_POINTERS = 16

internal data class TouchPointer(val id: Int, val x: Int, val y: Int)

internal data class TouchscreenEvent(val pointerId: Int, val action: Int, val x: Int, val y: Int)

internal class TouchscreenPointers {

    private val held = arrayOfNulls<TouchPointer>(TOUCHSCREEN_POINTERS)

    fun onTouch(
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPointer>,
    ): List<TouchscreenEvent> {
        val events = mutableListOf<TouchscreenEvent>()
        if (action == MotionEvent.ACTION_DOWN) releaseAll(events)
        val acting =
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) null
            else pointers[actionIndex]
        for (slot in held.indices) {
            val pointer = held[slot] ?: continue
            if (pointers.none { it.id == pointer.id }) release(slot, pointer, events)
        }
        for (pointer in pointers) {
            if (pointer.id != acting?.id) moveOrPress(pointer, events)
        }
        if (acting == null) return events
        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                slotOf(acting.id)?.let { release(it, checkNotNull(held[it]), events) }
                moveOrPress(acting, events)
            }
            MotionEvent.ACTION_POINTER_UP -> slotOf(acting.id)?.let { release(it, acting, events) }
            MotionEvent.ACTION_UP -> {
                slotOf(acting.id)?.let { release(it, acting, events) }
                releaseAll(events)
            }
        }
        return events
    }

    private fun moveOrPress(pointer: TouchPointer, events: MutableList<TouchscreenEvent>) {
        val slot = slotOf(pointer.id)
        if (slot == null) {
            val free = held.indexOfFirst { it == null }
            if (free < 0) return
            held[free] = pointer
            events += TouchscreenEvent(free, MotionEvent.ACTION_DOWN, pointer.x, pointer.y)
        } else if (held[slot] != pointer) {
            held[slot] = pointer
            events += TouchscreenEvent(slot, MotionEvent.ACTION_MOVE, pointer.x, pointer.y)
        }
    }

    private fun release(slot: Int, pointer: TouchPointer, events: MutableList<TouchscreenEvent>) {
        if (held[slot] != pointer) {
            events += TouchscreenEvent(slot, MotionEvent.ACTION_MOVE, pointer.x, pointer.y)
        }
        events += TouchscreenEvent(slot, MotionEvent.ACTION_UP, pointer.x, pointer.y)
        held[slot] = null
    }

    private fun releaseAll(events: MutableList<TouchscreenEvent>) {
        for (slot in held.indices) held[slot]?.let { release(slot, it, events) }
    }

    private fun slotOf(id: Int): Int? = held.indexOfFirst { it?.id == id }.takeIf { it >= 0 }
}
