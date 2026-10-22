package io.github.octoclonius.libreauto.privileged

import android.os.Parcel
import android.os.Parcelable

class AudioOutput(val usages: IntArray, val sampleRate: Int, val channelCount: Int) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeIntArray(usages)
        dest.writeInt(sampleRate)
        dest.writeInt(channelCount)
    }

    companion object CREATOR : Parcelable.Creator<AudioOutput> {

        override fun createFromParcel(source: Parcel): AudioOutput =
            AudioOutput(checkNotNull(source.createIntArray()), source.readInt(), source.readInt())

        override fun newArray(size: Int): Array<AudioOutput?> = arrayOfNulls(size)
    }
}
