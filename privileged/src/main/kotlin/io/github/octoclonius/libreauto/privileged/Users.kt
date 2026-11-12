package io.github.octoclonius.libreauto.privileged

import android.os.UserHandle

private val getIdentifier by lazy { UserHandle::class.java.getMethod("getIdentifier") }

internal val UserHandle.userId: Int
    get() = getIdentifier.invoke(this) as Int
