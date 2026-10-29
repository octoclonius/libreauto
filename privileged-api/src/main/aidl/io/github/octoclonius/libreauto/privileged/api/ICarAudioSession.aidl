package io.github.octoclonius.libreauto.privileged.api;

import android.os.ParcelFileDescriptor;

interface ICarAudioSession {
    ParcelFileDescriptor[] getPipes() = 1;

    void stop() = 2;
}
