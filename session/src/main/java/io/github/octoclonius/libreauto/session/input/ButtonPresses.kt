package io.github.octoclonius.libreauto.session.input

internal class ButtonPresses {

    private val pressed = HashSet<Int>()

    fun isNewPress(code: Int, isPressed: Boolean): Boolean =
        if (isPressed) pressed.add(code) else !pressed.remove(code)
}
