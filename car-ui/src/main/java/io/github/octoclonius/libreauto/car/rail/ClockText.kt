package io.github.octoclonius.libreauto.car.rail

import java.text.CharacterIterator
import java.text.DateFormat
import java.util.Date

fun clockText(format: DateFormat, date: Date): String {
    val iterator = format.formatToCharacterIterator(date)
    val text = StringBuilder()
    var char = iterator.first()
    while (char != CharacterIterator.DONE) {
        if (iterator.getAttribute(DateFormat.Field.AM_PM) == null) text.append(char)
        char = iterator.next()
    }
    return text.trim().toString()
}
