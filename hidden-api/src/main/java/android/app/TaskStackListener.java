package android.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.os.RemoteException;

public abstract class TaskStackListener extends ITaskStackListener.Stub {
    public TaskStackListener() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskStackChanged() throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskRemoved(int taskId) throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskMovedToBack(RunningTaskInfo taskInfo) throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onTaskDisplayChanged(int taskId, int newDisplayId) throws RemoteException {
        throw new RuntimeException("Stub!");
    }
}
