package android.app;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import java.util.List;

public interface IActivityTaskManager extends IInterface {
    int startActivityFromRecents(int taskId, Bundle options) throws RemoteException;

    boolean removeTask(int taskId) throws RemoteException;

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents,
            boolean keepIntentExtra, int displayId) throws RemoteException;

    void moveRootTaskToDisplayOnTopOrBottom(int taskId, int displayId, boolean onTop)
            throws RemoteException;

    void registerTaskStackListener(ITaskStackListener listener) throws RemoteException;

    void unregisterTaskStackListener(ITaskStackListener listener) throws RemoteException;

    abstract class Stub extends Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
