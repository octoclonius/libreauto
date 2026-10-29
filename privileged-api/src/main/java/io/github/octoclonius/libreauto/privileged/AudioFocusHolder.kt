package io.github.octoclonius.libreauto.privileged

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioFocusHolder(val clientId: String, val uid: Int, val usage: Int, val gain: Int) :
    Parcelable
