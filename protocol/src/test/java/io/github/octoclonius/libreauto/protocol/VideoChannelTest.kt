package io.github.octoclonius.libreauto.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VideoChannelTest {

    @Test
    fun theVideoChannelIsTheMediaSinkWithVideoConfigs() {
        assertEquals(2, pick(sink(1, varint(1, 1), message(3)), sink(2, message(4))))
    }

    @Test
    fun theTopLevelCodecDoesNotDecide() {
        assertEquals(1, pick(sink(1, varint(1, 1), message(4))))
        assertNull(pick(sink(1, varint(1, 3))))
    }

    @Test
    fun aVideoSinkForTheClusterOrAnAuxiliaryDisplayIsSkipped() {
        assertEquals(
            3,
            pick(
                sink(1, message(4), varint(7, 1)),
                sink(2, message(4), varint(7, 2)),
                sink(3, message(4), varint(7, 0)),
            ),
        )
    }

    @Test
    fun theDisplayIdDoesNotDecide() {
        assertEquals(1, pick(sink(1, message(4), varint(6, 1))))
    }

    @Test
    fun otherServicesAreSkipped() {
        assertNull(pick(service(1, message(2)), service(2, message(5, message(4)))))
    }

    @Test
    fun theFirstMainDisplayVideoSinkIsPicked() {
        assertEquals(5, pick(sink(5, message(4)), sink(6, message(4))))
    }

    @Test
    fun theSinkKeepsItsConfigsInOrder() {
        val sink =
            videoSink(
                parseServiceDiscoveryResponse(
                    message(1, sink(3, message(4, varint(1, 1)), message(4, varint(1, 2))))
                )
            )
        assertEquals(listOf(1, 2), sink?.configs?.map { it.resolution })
    }

    @Test
    fun theSinkHasItsDisplayIdAsField6Or0() {
        assertEquals(2, sinkOf(sink(1, message(4), varint(6, 2)))?.displayId)
        assertEquals(0, sinkOf(sink(1, message(4)))?.displayId)
    }

    private fun sinkOf(vararg services: ByteArray) =
        videoSink(
            parseServiceDiscoveryResponse(services.map { message(1, it) }.reduce(ByteArray::plus))
        )

    private fun pick(vararg services: ByteArray) = sinkOf(*services)?.channel

    private fun sink(channel: Int, vararg fields: ByteArray) =
        service(channel, message(3, *fields))

    private fun service(channel: Int, service: ByteArray) = varint(1, channel) + service

    private fun varint(number: Int, value: Int) =
        byteArrayOf((number shl 3).toByte(), value.toByte())

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
