package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface INotificationManager extends IInterface {
    void setInterruptionFilter(String pkg, int interruptionFilter, boolean fromUser)
            throws RemoteException;

    abstract class Stub extends Binder implements INotificationManager {
        public static INotificationManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
