package io.github.octoclonius.libreauto.privileged

import java.lang.reflect.InvocationTargetException

internal fun Exception.unwrapped(): Throwable =
    if (this is InvocationTargetException) targetException else this
