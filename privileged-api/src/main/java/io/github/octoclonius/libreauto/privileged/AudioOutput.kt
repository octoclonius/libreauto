package io.github.octoclonius.libreauto.privileged

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class AudioOutput(val usages: IntArray, val sampleRate: Int, val channelCount: Int) : Parcelable
