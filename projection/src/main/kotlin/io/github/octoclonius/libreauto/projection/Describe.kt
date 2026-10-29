package io.github.octoclonius.libreauto.projection

internal const val MAX_DESCRIPTION_CHARS = 256

internal fun describe(e: Throwable): String =
    listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ").take(MAX_DESCRIPTION_CHARS)
