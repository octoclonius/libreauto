package io.github.octoclonius.libreauto.privileged.notifications

internal fun notificationVisibilityRequestError(rank: Int, count: Int): String? =
    if (rank in 0 until count) null
    else "The rank $rank isn't one of the $count notifications shown"
