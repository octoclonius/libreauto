package io.github.octoclonius.libreauto.privileged

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.audiopolicy.AudioMix
import android.media.audiopolicy.AudioMixingRule
import android.media.audiopolicy.AudioPolicy
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException

internal class CarAudioPolicy
private constructor(
    private val audioManager: AudioManager,
    private val policy: AudioPolicy,
    private val sinks: List<MixSink>,
) : Closeable {

    companion object {
        private const val TAG = "CarAudioPolicy"
        private const val REGISTERED = 0

        fun register(
            context: Context,
            outputs: List<AudioOutput>,
            looper: Looper,
            onFocusChanged: () -> Unit,
        ): CarAudioPolicy {
            val mixes = outputs.map(::mix)
            val builder = AudioPolicy.Builder(context).setLooper(looper)
            for (mix in mixes) builder.addMix(mix)
            builder.setAudioPolicyFocusListener(
                object : AudioPolicy.AudioPolicyFocusListener() {
                    override fun onAudioFocusGrant(afi: AudioFocusInfo, requestResult: Int) {
                        onFocusChanged()
                    }

                    override fun onAudioFocusLoss(afi: AudioFocusInfo, wasNotified: Boolean) {
                        onFocusChanged()
                    }
                }
            )
            val policy = builder.build()
            val audioManager = context.getSystemService(AudioManager::class.java)
            val status =
                AudioManager::class
                    .java
                    .getMethod("registerAudioPolicy", AudioPolicy::class.java)
                    .invoke(audioManager, policy) as Int
            check(status == REGISTERED) { "Registering the audio policy failed: $status" }
            val sinks = mutableListOf<MixSink>()
            try {
                for ((mix, output) in mixes.zip(outputs)) {
                    sinks += MixSink(record(context, policy, mix, output), output)
                }
            } catch (e: Exception) {
                sinks.forEach(MixSink::close)
                unregister(audioManager, policy)
                throw e
            }
            sinks.forEach(MixSink::start)
            Log.i(
                TAG,
                "Diverting to the car: " +
                    outputs.joinToString {
                        "usages ${it.usages.toList()} at ${it.sampleRate} Hz, " +
                            "${it.channelCount} channels"
                    },
            )
            return CarAudioPolicy(audioManager, policy, sinks)
        }

        private fun mix(output: AudioOutput): AudioMix {
            val rule = AudioMixingRule.Builder()
            for (usage in output.usages) {
                rule.addMixRule(
                    AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE,
                    AudioAttributes.Builder().setUsage(usage).build(),
                )
            }
            return AudioMix.Builder(rule.build())
                .setFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(output.sampleRate)
                        .setChannelMask(
                            if (output.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO
                            else AudioFormat.CHANNEL_OUT_STEREO
                        )
                        .build()
                )
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build()
        }

        private fun record(
            context: Context,
            policy: AudioPolicy,
            mix: AudioMix,
            output: AudioOutput,
        ): AudioRecord {
            val inChannels =
                if (output.channelCount == 1) AudioFormat.CHANNEL_IN_MONO
                else AudioFormat.CHANNEL_IN_STEREO
            val builder =
                AudioRecord.Builder()
                    .setContext(context)
                    .setBufferSizeInBytes(
                        AudioRecord.getMinBufferSize(
                            output.sampleRate,
                            inChannels,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ) * 2
                    )
            return policy.createAudioRecordSink(mix, builder)
                ?: error("The audio policy made no recorder for usages ${output.usages.toList()}")
        }

        private fun unregister(audioManager: AudioManager, policy: AudioPolicy) {
            AudioManager::class
                .java
                .getMethod("unregisterAudioPolicy", AudioPolicy::class.java)
                .invoke(audioManager, policy)
        }
    }

    val pipes: List<ParcelFileDescriptor>
        get() = sinks.map { it.readEnd }

    fun focusStack(): List<AudioFocusInfo> = policy.focusStack

    override fun close() {
        try {
            sinks.forEach(MixSink::close)
        } finally {
            unregister(audioManager, policy)
            Log.i(TAG, "Stopped diverting audio to the car")
        }
    }

    private class MixSink(private val record: AudioRecord, output: AudioOutput) : Closeable {

        companion object {
            private const val CHUNK_MILLIS = 20
            private const val BYTES_PER_SAMPLE = 2
        }

        private val pipe = ParcelFileDescriptor.createPipe()
        private val chunkBytes =
            output.sampleRate * CHUNK_MILLIS / 1000 * output.channelCount * BYTES_PER_SAMPLE
        private val thread = Thread(::copy, "audio-mix-sink")
        @Volatile private var isClosed = false

        val readEnd: ParcelFileDescriptor
            get() = pipe[0]

        fun start() {
            record.startRecording()
            thread.start()
        }

        override fun close() {
            isClosed = true
            record.stop()
            if (thread.isAlive) thread.join()
            record.release()
            pipe[0].close()
            pipe[1].close()
        }

        private fun copy() {
            val buffer = ByteArray(chunkBytes)
            val out = FileOutputStream(pipe[1].fileDescriptor)
            try {
                while (!isClosed) {
                    val count = record.read(buffer, 0, buffer.size)
                    if (count < 0) {
                        if (!isClosed) Log.w(TAG, "Reading a mix failed: $count")
                        break
                    }
                    out.write(buffer, 0, count)
                }
            } catch (e: IOException) {
                if (!isClosed) Log.w(TAG, "Nothing reads a mix anymore", e)
            }
        }
    }
}
