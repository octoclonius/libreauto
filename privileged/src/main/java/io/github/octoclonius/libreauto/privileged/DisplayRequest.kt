package io.github.octoclonius.libreauto.privileged

internal fun displayRequestError(
    width: Int,
    height: Int,
    densityDpi: Int,
    framesPerSecond: Int,
    homePackage: String,
    appPackage: String,
): String? =
    when {
        width <= 0 || height <= 0 -> "The display size ${width}x$height is empty"
        densityDpi <= 0 -> "The display density $densityDpi isn't positive"
        framesPerSecond <= 0 -> "The display refresh rate $framesPerSecond isn't positive"
        homePackage != appPackage ->
            "The home component's package $homePackage isn't LibreAuto's $appPackage"
        else -> null
    }
