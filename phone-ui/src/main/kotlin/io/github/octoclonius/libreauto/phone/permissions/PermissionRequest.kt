package io.github.octoclonius.libreauto.phone.permissions

internal fun isDeniedWithoutDialog(
    isGranted: Boolean,
    wasRationaleShown: Boolean,
    isRationaleShown: Boolean,
): Boolean = !isGranted && !wasRationaleShown && !isRationaleShown
