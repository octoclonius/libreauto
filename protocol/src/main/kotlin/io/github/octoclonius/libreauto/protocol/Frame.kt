package io.github.octoclonius.libreauto.protocol

object FrameFlags {
    const val FIRST = 0x01
    const val LAST = 0x02
    const val CONTROL = 0x04
    const val ENCRYPTED = 0x08
}

internal const val FRAME_HEADER_SIZE = 4

internal const val TOTAL_SIZE_FIELD_SIZE = 4

internal const val MAX_FRAME_SIZE = 16128

const val MAX_RECORD_PLAINTEXT_SIZE = 16384
