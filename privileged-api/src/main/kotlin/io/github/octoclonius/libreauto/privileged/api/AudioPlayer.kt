package io.github.octoclonius.libreauto.privileged.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize data class AudioPlayer(val id: Int, val uid: Int, val usage: Int) : Parcelable
