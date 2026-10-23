package io.github.octoclonius.libreauto.privileged

import android.os.Parcel
import android.os.Parcelable

class AudioInput(val sampleRate: Int, val channelCount: Int) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(sampleRate)
        dest.writeInt(channelCount)
    }

    companion object CREATOR : Parcelable.Creator<AudioInput> {

        override fun createFromParcel(source: Parcel): AudioInput =
            AudioInput(source.readInt(), source.readInt())

        override fun newArray(size: Int): Array<AudioInput?> = arrayOfNulls(size)
    }
}
