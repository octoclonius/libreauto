package io.github.octoclonius.libreauto.privileged

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

const val NO_TASK = -1

@Parcelize data class DisplayTask(val taskId: Int, val packageName: String) : Parcelable
