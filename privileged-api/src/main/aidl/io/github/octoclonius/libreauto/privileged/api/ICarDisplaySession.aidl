package io.github.octoclonius.libreauto.privileged.api;

import android.app.PendingIntent;

interface ICarDisplaySession {
    int getDisplayId() = 1;

    void resize(int width, int height, int densityDpi) = 2;

    oneway void sendTouch(long eventTimeNanos, int action, int actionIndex, in int[] pointerIds, in int[] xs, in int[] ys) = 3;

    oneway void sendKey(int keyCode) = 4;

    oneway void showActivity(in PendingIntent activity) = 5;

    oneway void showTask(int taskId) = 6;

    oneway void closeTask(int taskId) = 7;

    oneway void restoreTasks(in int[] taskIds, int frontTaskId) = 8;

    void release() = 9;
}
