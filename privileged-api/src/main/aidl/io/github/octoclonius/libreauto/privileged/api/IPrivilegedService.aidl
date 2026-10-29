package io.github.octoclonius.libreauto.privileged.api;

import android.content.ComponentName;
import android.view.Surface;
import io.github.octoclonius.libreauto.privileged.api.AudioInput;
import io.github.octoclonius.libreauto.privileged.api.AudioOutput;
import io.github.octoclonius.libreauto.privileged.api.IAudioActivityListener;
import io.github.octoclonius.libreauto.privileged.api.ICarAudioSession;
import io.github.octoclonius.libreauto.privileged.api.ICarDisplaySession;
import io.github.octoclonius.libreauto.privileged.api.IDisplayTaskListener;
import io.github.octoclonius.libreauto.privileged.api.IPhoneBluetoothService;

interface IPrivilegedService {
    void destroy() = 16777114;

    int getUid() = 1;

    ICarDisplaySession createDisplay(in Surface surface, int width, int height, int densityDpi, in ComponentName home, IDisplayTaskListener tasks) = 2;

    oneway void exitAccessoryMode() = 5;

    ICarAudioSession startAudio(in AudioOutput[] outputs, in @nullable AudioInput input, IAudioActivityListener listener) = 7;

    oneway void setPhoneSetting(int setting, boolean isOn) = 13;

    void allowCallAccess() = 15;

    IPhoneBluetoothService getBluetoothService() = 26;
}
