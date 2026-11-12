package io.github.octoclonius.libreauto.privileged

import java.lang.reflect.InvocationTargetException

internal fun Throwable.containedCause(): Throwable =
    when (this) {
        is InvocationTargetException -> targetException
        is Exception,
        is LinkageError -> this
        else -> throw this
    }
