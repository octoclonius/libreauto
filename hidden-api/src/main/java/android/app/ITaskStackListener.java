package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface ITaskStackListener extends IInterface {
    void onTaskStackChanged() throws RemoteException;

    void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException;

    void onTaskRemoved(int taskId) throws RemoteException;

    void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) throws RemoteException;

    void onTaskMovedToBack(ActivityManager.RunningTaskInfo taskInfo) throws RemoteException;

    void onTaskDisplayChanged(int taskId, int newDisplayId) throws RemoteException;

    abstract class Stub extends Binder implements ITaskStackListener {
        public Stub() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
