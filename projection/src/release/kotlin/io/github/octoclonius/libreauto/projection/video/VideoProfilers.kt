package io.github.octoclonius.libreauto.projection.video

import android.content.Context

internal fun videoProfiler(@Suppress("UNUSED_PARAMETER") context: Context): VideoProfiler =
    object : VideoProfiler {}
