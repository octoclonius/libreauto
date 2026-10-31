package io.github.octoclonius.libreauto.projection.media

import android.media.MediaCodecList
import android.media.MediaFormat

internal fun encoderName(format: MediaFormat): String? =
    MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
