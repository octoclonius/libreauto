package io.github.octoclonius.libreauto.privileged;

import android.content.ComponentName;
import android.view.Surface;
import io.github.octoclonius.libreauto.privileged.AudioInput;
import io.github.octoclonius.libreauto.privileged.AudioOutput;
import io.github.octoclonius.libreauto.privileged.IAudioActivityListener;
import io.github.octoclonius.libreauto.privileged.ICarAudioSession;
import io.github.octoclonius.libreauto.privileged.ICarDisplaySession;
import io.github.octoclonius.libreauto.privileged.IDisplayTaskListener;
import io.github.octoclonius.libreauto.privileged.IPhoneBluetoothService;

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
