package com.android.internal.statusbar;

import android.os.Parcel;
import android.os.Parcelable;

public class NotificationVisibility implements Parcelable {
    public static final Parcelable.Creator<NotificationVisibility> CREATOR = null;

    public static NotificationVisibility obtain(String key, int rank, int count, boolean visible) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        throw new RuntimeException("Stub!");
    }
}
