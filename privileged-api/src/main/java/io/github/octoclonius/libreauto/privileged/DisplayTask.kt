package io.github.octoclonius.libreauto.privileged

import android.os.Parcel
import android.os.Parcelable

const val NO_TASK = -1

data class DisplayTask(val taskId: Int, val packageName: String) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(taskId)
        dest.writeString(packageName)
    }

    companion object CREATOR : Parcelable.Creator<DisplayTask> {

        override fun createFromParcel(source: Parcel): DisplayTask =
            DisplayTask(source.readInt(), source.readString().orEmpty())

        override fun newArray(size: Int): Array<DisplayTask?> = arrayOfNulls(size)
    }
}
