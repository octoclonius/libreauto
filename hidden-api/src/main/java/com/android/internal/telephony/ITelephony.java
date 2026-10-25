package com.android.internal.telephony;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface ITelephony extends IInterface {
    void setDataEnabledForReason(int subId, int reason, boolean enable, String callingPackage)
            throws RemoteException;

    abstract class Stub extends Binder implements ITelephony {
        public static ITelephony asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
