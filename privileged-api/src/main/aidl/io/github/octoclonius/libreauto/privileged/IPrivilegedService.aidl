package io.github.octoclonius.libreauto.privileged;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import io.github.octoclonius.libreauto.privileged.AudioInput;
import io.github.octoclonius.libreauto.privileged.AudioOutput;
import io.github.octoclonius.libreauto.privileged.IAudioActivityListener;
import io.github.octoclonius.libreauto.privileged.IDisplayTaskListener;

interface IPrivilegedService {
    void destroy() = 16777114;

    int getUid() = 1;

    int createDisplay(in Surface surface, int width, int height, int densityDpi, in ComponentName home, IDisplayTaskListener tasks) = 2;

    void releaseDisplay(int displayId) = 3;

    oneway void sendTouch(long eventTimeNanos, int action, int actionIndex, in int[] pointerIds, in int[] xs, in int[] ys) = 4;

    oneway void exitAccessoryMode() = 5;

    oneway void sendKey(int displayId, int keyCode) = 6;

    ParcelFileDescriptor[] startAudio(in AudioOutput[] outputs, in @nullable AudioInput input, IAudioActivityListener listener) = 7;

    void stopAudio() = 8;

    oneway void showHome(int displayId, in PendingIntent home) = 9;

    void resizeDisplay(int displayId, int width, int height, int densityDpi) = 10;

    oneway void showTask(int displayId, int taskId) = 11;

    oneway void closeTask(int displayId, int taskId) = 12;
}
