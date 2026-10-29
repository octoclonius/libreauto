package io.github.octoclonius.libreauto.privileged.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiopolicy.AudioMix
import android.media.audiopolicy.AudioMixingRule
import android.media.audiopolicy.AudioPolicy
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import io.github.octoclonius.libreauto.privileged.api.AudioInput
import io.github.octoclonius.libreauto.privileged.api.AudioOutput
import io.github.octoclonius.libreauto.privileged.closeAll
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException

private const val CHUNK_MILLIS = 20

internal class CarAudioPolicy
private constructor(
    private val audioManager: AudioManager,
    private val policy: AudioPolicy,
    private val sinks: List<MixSink>,
    private val source: MixSource?,
) : Closeable {

    companion object {
        private const val TAG = "CarAudioPolicy"

        val MICROPHONE_SOURCES =
            setOf(
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_PERFORMANCE,
            )

        private val setCapturePreset =
            AudioAttributes.Builder::class
                .java
                .getMethod("setCapturePreset", Int::class.javaPrimitiveType)

        fun register(
            context: Context,
            outputs: List<AudioOutput>,
            input: AudioInput?,
            looper: Looper,
            onFocusChanged: () -> Unit,
        ): CarAudioPolicy {
            val mixes = outputs.map(::mix)
            val inputMix = input?.let { it to inputMix(it) }
            val builder = AudioPolicy.Builder(context).setLooper(looper)
            for (mix in mixes) builder.addMix(mix)
            inputMix?.let { builder.addMix(it.second) }
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
            val success = AudioManager::class.java.getField("SUCCESS").getInt(null)
            val status =
                AudioManager::class
                    .java
                    .getMethod("registerAudioPolicy", AudioPolicy::class.java)
                    .invoke(audioManager, policy) as Int
            check(status == success) { "Registering the audio policy failed: $status" }
            val sinks = mutableListOf<MixSink>()
            val source =
                try {
                    for ((mix, output) in mixes.zip(outputs)) {
                        sinks += MixSink(record(context, policy, mix, output))
                    }
                    inputMix?.let { (input, mix) -> MixSource(inject(context, policy, mix, input)) }
                } catch (e: Exception) {
                    sinks.forEach(MixSink::close)
                    unregister(audioManager, policy)
                    throw e
                }
            sinks.forEach(MixSink::start)
            source?.start()
            if (outputs.isNotEmpty()) {
                Log.i(
                    TAG,
                    "Diverting to the car: " +
                        outputs.joinToString {
                            "usages ${it.usages.toList()} at ${it.sampleRate} Hz, " +
                                "${it.channelCount} channels"
                        },
                )
            }
            if (input != null) {
                Log.i(
                    TAG,
                    "Recordings of sources ${MICROPHONE_SOURCES.sorted()} get the car's " +
                        "microphone at ${input.sampleRate} Hz, ${input.channelCount} channels",
                )
            }
            return CarAudioPolicy(audioManager, policy, sinks, source)
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
                .setFormat(format(output.sampleRate, output.channelCount))
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build()
        }

        private fun inputMix(input: AudioInput): AudioMix {
            val rule = AudioMixingRule.Builder()
            for (source in MICROPHONE_SOURCES) {
                val attributes = AudioAttributes.Builder()
                setCapturePreset.invoke(attributes, source)
                rule.addMixRule(
                    AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                    attributes.build(),
                )
            }
            return AudioMix.Builder(rule.build())
                .setFormat(format(input.sampleRate, input.channelCount))
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build()
        }

        private fun format(sampleRate: Int, channelCount: Int): AudioFormat =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(outChannelMask(channelCount))
                .build()

        private fun outChannelMask(channelCount: Int): Int =
            if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

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

        private fun inject(
            context: Context,
            policy: AudioPolicy,
            mix: AudioMix,
            input: AudioInput,
        ): AudioTrack {
            val builder =
                AudioTrack.Builder()
                    .setContext(context)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(
                        AudioTrack.getMinBufferSize(
                            input.sampleRate,
                            outChannelMask(input.channelCount),
                            AudioFormat.ENCODING_PCM_16BIT,
                        ) * 2
                    )
            return policy.createAudioTrackSource(mix, builder)
                ?: error("The audio policy made no track for the car's microphone")
        }

        private fun unregister(audioManager: AudioManager, policy: AudioPolicy) {
            AudioManager::class
                .java
                .getMethod("unregisterAudioPolicy", AudioPolicy::class.java)
                .invoke(audioManager, policy)
        }
    }

    val pipes: List<ParcelFileDescriptor>
        get() = sinks.map { it.readEnd } + listOfNotNull(source?.writeEnd)

    fun focusStack(): List<AudioFocusInfo> = policy.focusStack

    override fun close() {
        closeAll(TAG) {
            for (sink in sinks) step("Stopping a mix") { sink.close() }
            step("Stopping the microphone source") { source?.close() }
            step("Unregistering the audio policy") { unregister(audioManager, policy) }
        }
        Log.i(TAG, "Stopped diverting audio to the car")
    }

    private class MixSink(private val record: AudioRecord) : Closeable {

        private val pipe = ParcelFileDescriptor.createPipe()
        private val chunkBytes =
            record.format.frameSizeInBytes * record.sampleRate * CHUNK_MILLIS / 1000
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
            closeAll(TAG) {
                step("Stopping a mix's recording") { record.stop() }
                step("Waiting for a mix's copy") { if (thread.isAlive) thread.join() }
                step("Releasing a mix's recording") { record.release() }
                step("Closing a mix's read end") { pipe[0].close() }
                step("Closing a mix's write end") { pipe[1].close() }
            }
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

    private class MixSource(private val track: AudioTrack) : Closeable {

        private val pipe = ParcelFileDescriptor.createPipe()
        private val wakeUp = ParcelFileDescriptor.createPipe()
        private val chunkBytes =
            track.format.frameSizeInBytes * track.sampleRate * CHUNK_MILLIS / 1000
        private val thread = Thread(::copy, "audio-mix-source")

        val writeEnd: ParcelFileDescriptor
            get() = pipe[1]

        fun start() {
            track.play()
            thread.start()
        }

        override fun close() {
            closeAll(TAG) {
                step("Waking the microphone copy") { wakeUp[1].close() }
                step("Waiting for the microphone copy") { if (thread.isAlive) thread.join() }
                step("Stopping the microphone track") { track.stop() }
                step("Releasing the microphone track") { track.release() }
                step("Closing the microphone's read end") { pipe[0].close() }
                step("Closing the microphone's write end") { pipe[1].close() }
                step("Closing the microphone's wake-up pipe") { wakeUp[0].close() }
            }
        }

        private fun copy() {
            val buffer = ByteArray(chunkBytes)
            val readable =
                StructPollfd().apply {
                    fd = pipe[0].fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
            val closing =
                StructPollfd().apply {
                    fd = wakeUp[0].fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
            try {
                while (true) {
                    Os.poll(arrayOf(readable, closing), -1)
                    if (closing.revents.toInt() != 0) break
                    val count = Os.read(pipe[0].fileDescriptor, buffer, 0, buffer.size)
                    if (count == 0) {
                        Log.i(TAG, "Nothing writes the car's microphone anymore")
                        break
                    }
                    val written = track.write(buffer, 0, count)
                    if (written < 0) {
                        Log.w(TAG, "Injecting the car's microphone failed: $written")
                        break
                    }
                }
            } catch (e: ErrnoException) {
                Log.w(TAG, "Reading the car's microphone failed", e)
            } catch (e: InterruptedIOException) {
                Log.w(TAG, "Reading the car's microphone was interrupted", e)
            }
        }
    }
}
