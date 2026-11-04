package io.github.octoclonius.libreauto.privileged.notifications

internal fun notificationAccessRequestError(listenerPackage: String, appPackage: String): String? =
    if (listenerPackage == appPackage) null
    else "The listener's package $listenerPackage isn't LibreAuto's $appPackage"

internal fun notificationClickRequestError(rank: Int, count: Int): String? =
    if (rank in 0 until count) null
    else "The rank $rank isn't one of the $count notifications shown"
