package io.github.octoclonius.libreauto.projection.media

import android.media.MediaCodecList
import android.media.MediaFormat

internal class EncoderInfo(val name: String, val isHardware: Boolean)

internal fun findEncoder(format: MediaFormat): EncoderInfo? {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    val supporting =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                info.getCapabilitiesForType(mime).isFormatSupported(format)
        }
    val info =
        supporting.firstOrNull { it.isHardwareAccelerated }
            ?: supporting.firstOrNull()
            ?: return null
    return EncoderInfo(info.name, info.isHardwareAccelerated)
}
