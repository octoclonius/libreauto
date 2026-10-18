package io.github.octoclonius.libreauto.privileged;

import android.content.ComponentName;
import android.view.Surface;

interface IPrivilegedService {
    void destroy() = 16777114;

    int getUid() = 1;

    int createDisplay(in Surface surface, int width, int height, int densityDpi, int framesPerSecond, in ComponentName home) = 2;

    void releaseDisplay(int displayId) = 3;

    oneway void sendTouch(int action, int actionIndex, in int[] pointerIds, in int[] xs, in int[] ys) = 4;
}
