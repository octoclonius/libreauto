package io.github.octoclonius.libreauto.protocol

import java.io.IOException
import java.net.ProtocolException
import java.nio.ByteBuffer

private const val TIMESTAMP_SIZE = 8

fun mediaData(timestampUs: Long, data: ByteArray): ByteArray =
    ByteBuffer.allocate(TIMESTAMP_SIZE + data.size).putLong(timestampUs).put(data).array()

@Throws(IOException::class)
fun parseMediaAck(body: ByteArray): Int {
    val count = MediaAck.ADAPTER.decode(body).count
    if (count == null || count < 1) throw ProtocolException("Ack of $count frames")
    return count
}
