package com.android.internal.statusbar;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IStatusBarService extends IInterface {
    void onNotificationClick(String key, NotificationVisibility nv) throws RemoteException;

    void onNotificationClear(
            String pkg,
            int userId,
            String key,
            int dismissalSurface,
            int dismissalSentiment,
            NotificationVisibility nv,
            boolean fromBundle)
            throws RemoteException;

    abstract class Stub extends Binder implements IStatusBarService {
        public static IStatusBarService asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
