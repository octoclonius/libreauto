package io.github.octoclonius.libreauto.privileged.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize class AudioInput(val sampleRate: Int, val channelCount: Int) : Parcelable
