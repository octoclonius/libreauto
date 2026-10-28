package io.github.octoclonius.libreauto.privileged

internal fun activityRequestError(isActivity: Boolean, creatorUid: Int, appUid: Int): String? =
    when {
        !isActivity -> "The intent doesn't start an activity"
        creatorUid != appUid -> "The intent was created by uid $creatorUid, not LibreAuto"
        else -> null
    }
