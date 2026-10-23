package io.github.octoclonius.libreauto.privileged

internal fun homeRequestError(
    displayId: Int,
    carDisplayId: Int?,
    isActivity: Boolean,
    creatorUid: Int,
    appUid: Int,
): String? =
    when {
        carDisplayId == null -> "There is no car display"
        displayId != carDisplayId -> "The display $displayId isn't the car display $carDisplayId"
        !isActivity -> "The home intent doesn't start an activity"
        creatorUid != appUid -> "The home intent was created by uid $creatorUid, not LibreAuto"
        else -> null
    }
