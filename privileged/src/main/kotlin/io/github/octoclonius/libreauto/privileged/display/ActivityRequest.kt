package io.github.octoclonius.libreauto.privileged.display

internal fun activityRequestError(isActivity: Boolean): String? =
    if (isActivity) null else "The intent doesn't start an activity"
