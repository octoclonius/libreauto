package android.net.wifi;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IWifiManager extends IInterface {
    boolean setWifiEnabled(String packageName, boolean enable) throws RemoteException;

    abstract class Stub extends Binder implements IWifiManager {
        public static IWifiManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
