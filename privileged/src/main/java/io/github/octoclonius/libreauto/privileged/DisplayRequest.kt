package io.github.octoclonius.libreauto.privileged

internal fun resizeRequestError(
    displayId: Int,
    width: Int,
    height: Int,
    densityDpi: Int,
    currentId: Int?,
): String? =
    when {
        displayId != currentId -> "The display $displayId isn't the car display $currentId"
        width <= 0 || height <= 0 -> "The display size ${width}x$height is empty"
        densityDpi <= 0 -> "The display density $densityDpi isn't positive"
        else -> null
    }

internal fun displayRequestError(
    width: Int,
    height: Int,
    densityDpi: Int,
    homePackage: String,
    appPackage: String,
): String? =
    when {
        width <= 0 || height <= 0 -> "The display size ${width}x$height is empty"
        densityDpi <= 0 -> "The display density $densityDpi isn't positive"
        homePackage != appPackage ->
            "The home component's package $homePackage isn't LibreAuto's $appPackage"
        else -> null
    }
