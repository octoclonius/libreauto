package io.github.octoclonius.libreauto.projection.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.util.Log
import io.github.octoclonius.libreauto.projection.media.encoderName
import io.github.octoclonius.libreauto.protocol.media.AudioCodec
import io.github.octoclonius.libreauto.protocol.media.PcmConfig
import io.github.octoclonius.libreauto.protocol.media.pcmFrameBytes
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class AacFrame(val timeUs: Long, val data: ByteArray)

internal class AacEncoder
private constructor(
    private val mediaCodec: MediaCodec,
    private val config: PcmConfig,
    private val isAdts: Boolean,
) : Closeable {

    companion object {
        private const val TAG = "AacEncoder"
        internal const val FRAME_SAMPLES = 1024
        internal const val MAX_BIT_RATE = 512_000
        private const val ADTS_HEADER_BYTES = 7
        private const val ADTS_SYNC = 0xFFF
        private const val ADTS_MPEG_2 = 1
        private const val ADTS_NO_CRC = 1
        private const val AAC_LC_OBJECT_TYPE = 2
        private const val ADTS_VARIABLE_BIT_RATE_FULLNESS = 0x7FF
        private val ADTS_SAMPLE_RATES =
            intArrayOf(
                96000,
                88200,
                64000,
                48000,
                44100,
                32000,
                24000,
                22050,
                16000,
                12000,
                11025,
                8000,
                7350,
            )

        fun bitRate(config: PcmConfig): Int =
            minOf(pcmFrameBytes(config, config.sampleRate) * Byte.SIZE_BITS / 2, MAX_BIT_RATE)

        fun isEncodable(config: PcmConfig): Boolean = encoderName(format(config)) != null

        fun isAdtsEncodable(config: PcmConfig): Boolean =
            config.sampleRate in ADTS_SAMPLE_RATES && isEncodable(config)

        fun adtsHeader(config: PcmConfig, frameBytes: Int): ByteArray {
            val rateIndex = ADTS_SAMPLE_RATES.indexOf(config.sampleRate)
            require(rateIndex >= 0) { "ADTS has no index for ${config.sampleRate} Hz" }
            val length = ADTS_HEADER_BYTES + frameBytes
            val channels = config.channelCount
            val fullness = ADTS_VARIABLE_BIT_RATE_FULLNESS
            return byteArrayOf(
                (ADTS_SYNC shr 4).toByte(),
                ((ADTS_SYNC and 0xF) shl 4 or (ADTS_MPEG_2 shl 3) or ADTS_NO_CRC).toByte(),
                ((AAC_LC_OBJECT_TYPE - 1) shl 6 or (rateIndex shl 2) or (channels shr 2)).toByte(),
                ((channels and 0x3) shl 6 or (length shr 11)).toByte(),
                (length shr 3).toByte(),
                ((length and 0x7) shl 5 or (fullness shr 6)).toByte(),
                ((fullness and 0x3F) shl 2).toByte(),
            )
        }

        fun create(config: PcmConfig, isAdts: Boolean): AacEncoder? {
            val name = encoderName(format(config))
            if (name == null) {
                Log.w(TAG, "No AAC encoder for ${config.sampleRate} Hz, ${config.channelCount} ch")
                return null
            }
            val mediaCodec =
                try {
                    MediaCodec.createByCodecName(name)
                } catch (e: IOException) {
                    Log.w(TAG, "Can't create the AAC encoder $name", e)
                    return null
                }
            val bitRate: Int
            try {
                val capabilities =
                    mediaCodec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
                bitRate =
                    checkNotNull(capabilities.audioCapabilities).bitrateRange.clamp(bitRate(config))
                val format = format(config).apply { setInteger(MediaFormat.KEY_BIT_RATE, bitRate) }
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                mediaCodec.start()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "The AAC encoder $name failed to start", e)
                mediaCodec.release()
                return null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "The AAC encoder $name refused the format", e)
                mediaCodec.release()
                return null
            }
            Log.i(
                TAG,
                "Encoding ${config.sampleRate} Hz, ${config.channelCount} ch at " +
                    "$bitRate bit/s with $name${if (isAdts) " in ADTS" else ""}",
            )
            return AacEncoder(mediaCodec, config, isAdts)
        }

        private fun format(config: PcmConfig): MediaFormat =
            MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    config.sampleRate,
                    config.channelCount,
                )
                .apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, CodecProfileLevel.AACObjectLC)
                }
    }

    val frameNanos: Long = TimeUnit.SECONDS.toNanos(FRAME_SAMPLES.toLong()) / config.sampleRate

    var codecConfig: ByteArray? = null
        private set

    private val sampleBytes = pcmFrameBytes(config, 1)
    private val info = MediaCodec.BufferInfo()
    private var hasInput = false

    fun encode(pcm: ByteArray, timeUs: Long): List<AacFrame> {
        var offset = 0
        while (offset < pcm.size) {
            val index = mediaCodec.dequeueInputBuffer(TimeUnit.NANOSECONDS.toMicros(frameNanos))
            if (index < 0) {
                Log.w(
                    TAG,
                    "The AAC encoder has no input buffer, dropping ${pcm.size - offset} bytes",
                )
                break
            }
            val buffer = checkNotNull(mediaCodec.getInputBuffer(index))
            buffer.clear()
            val size = minOf(pcm.size - offset, buffer.remaining())
            buffer.put(pcm, offset, size)
            val offsetUs =
                TimeUnit.SECONDS.toMicros((offset / sampleBytes).toLong()) / config.sampleRate
            mediaCodec.queueInputBuffer(index, 0, size, timeUs + offsetUs, 0)
            hasInput = true
            offset += size
        }
        return drain()
    }

    fun flush() {
        if (!hasInput) return
        mediaCodec.flush()
        hasInput = false
    }

    override fun close() {
        mediaCodec.release()
    }

    private fun drain(): List<AacFrame> {
        val frames = mutableListOf<AacFrame>()
        while (true) {
            val index = mediaCodec.dequeueOutputBuffer(info, 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return frames
            if (index < 0) continue
            val buffer = checkNotNull(mediaCodec.getOutputBuffer(index))
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            mediaCodec.releaseOutputBuffer(index, false)
            when {
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> codecConfig = data
                data.isNotEmpty() ->
                    frames +=
                        AacFrame(
                            info.presentationTimeUs,
                            if (isAdts) adtsHeader(config, data.size) + data else data,
                        )
            }
        }
    }
}

internal fun isEncodable(codec: AudioCodec, config: PcmConfig): Boolean =
    when (codec) {
        AudioCodec.PCM -> true
        AudioCodec.AAC_LC -> AacEncoder.isEncodable(config)
        AudioCodec.AAC_LC_ADTS -> AacEncoder.isAdtsEncodable(config)
    }
