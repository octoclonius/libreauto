package io.github.octoclonius.libreauto.privileged.api;

import android.content.ComponentName;
import android.os.UserHandle;
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

    oneway void acceptRingingCall(int videoState) = 30;

    oneway void endCall() = 31;

    void allowNotificationAccess(in ComponentName listener) = 27;

    oneway void onNotificationClick(String key, int rank, int count) = 28;

    oneway void onNotificationClear(String pkg, in UserHandle user, String key, boolean isHeadsUp, int rank, int count) = 29;

    IPhoneBluetoothService getBluetoothService() = 26;
}
