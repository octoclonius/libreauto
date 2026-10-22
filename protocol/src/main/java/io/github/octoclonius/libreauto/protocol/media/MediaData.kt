package io.github.octoclonius.libreauto.protocol.media

import java.io.IOException
import java.net.ProtocolException
import java.nio.ByteBuffer

private const val TIMESTAMP_SIZE = 8

fun mediaData(timestampUs: Long, data: ByteArray): ByteArray =
    ByteBuffer.allocate(TIMESTAMP_SIZE + data.size).putLong(timestampUs).put(data).array()

class MediaAcknowledgment(val session: Int?, val count: Int)

@Throws(IOException::class)
fun parseMediaAck(body: ByteArray): MediaAcknowledgment {
    val ack = MediaAck.ADAPTER.decode(body)
    val count = ack.count
    if (count == null || count < 1) throw ProtocolException("Ack of $count frames")
    return MediaAcknowledgment(ack.session, count)
}
