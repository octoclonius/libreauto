package io.github.octoclonius.libreauto.privileged

import android.os.Parcel
import android.os.Parcelable

data class AudioPlayer(val id: Int, val uid: Int, val usage: Int) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id)
        dest.writeInt(uid)
        dest.writeInt(usage)
    }

    companion object CREATOR : Parcelable.Creator<AudioPlayer> {

        override fun createFromParcel(source: Parcel): AudioPlayer =
            AudioPlayer(source.readInt(), source.readInt(), source.readInt())

        override fun newArray(size: Int): Array<AudioPlayer?> = arrayOfNulls(size)
    }
}
