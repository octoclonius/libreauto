package com.android.internal.telephony;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface ISub extends IInterface {
    int getDefaultDataSubId() throws RemoteException;

    abstract class Stub extends Binder implements ISub {
        public static ISub asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
