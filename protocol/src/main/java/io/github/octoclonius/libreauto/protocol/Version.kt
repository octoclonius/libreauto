package io.github.octoclonius.libreauto.protocol

import java.net.ProtocolException
import java.nio.ByteBuffer

internal const val VERSION_REQUEST_SIZE = 4
private const val VERSION_RESPONSE_SIZE = 6

data class ProtocolVersion(val major: Int, val minor: Int) : Comparable<ProtocolVersion> {
    init {
        require(major in 0..0xFFFF && minor in 0..0xFFFF) { "Version $this doesn't fit 2 bytes" }
    }

    override fun compareTo(other: ProtocolVersion) =
        compareValuesBy(this, other, { it.major }, { it.minor })

    override fun toString() = "$major.$minor"

    companion object {
        private val PHONE_VERSION_OLD = ProtocolVersion(1, 7)
        private val PHONE_VERSION_NEW = ProtocolVersion(6, 1)

        fun answerTo(car: ProtocolVersion) =
            if (car <= PHONE_VERSION_OLD) PHONE_VERSION_OLD else PHONE_VERSION_NEW
    }
}

@Throws(ProtocolException::class)
fun parseVersionRequest(body: ByteArray): ProtocolVersion {
    if (body.size < VERSION_REQUEST_SIZE) {
        throw ProtocolException("Version request of ${body.size} bytes")
    }
    val buffer = ByteBuffer.wrap(body)
    return ProtocolVersion(buffer.short.toUShort().toInt(), buffer.short.toUShort().toInt())
}

class VersionResponse(val version: ProtocolVersion, val status: Int) {
    fun toBody(): ByteArray =
        ByteBuffer.allocate(VERSION_RESPONSE_SIZE)
            .putShort(version.major.toShort())
            .putShort(version.minor.toShort())
            .putShort(status.toShort())
            .array()
}
