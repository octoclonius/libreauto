package io.github.octoclonius.libreauto.privileged

import android.os.Parcel
import android.os.Parcelable

data class AudioFocusHolder(val clientId: String, val uid: Int, val usage: Int, val gain: Int) :
    Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(clientId)
        dest.writeInt(uid)
        dest.writeInt(usage)
        dest.writeInt(gain)
    }

    companion object CREATOR : Parcelable.Creator<AudioFocusHolder> {

        override fun createFromParcel(source: Parcel): AudioFocusHolder =
            AudioFocusHolder(
                checkNotNull(source.readString()),
                source.readInt(),
                source.readInt(),
                source.readInt(),
            )

        override fun newArray(size: Int): Array<AudioFocusHolder?> = arrayOfNulls(size)
    }
}
