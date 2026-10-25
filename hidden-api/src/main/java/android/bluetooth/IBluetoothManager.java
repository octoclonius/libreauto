package android.bluetooth;

import android.content.AttributionSource;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IBluetoothManager extends IInterface {
    boolean enable(AttributionSource attributionSource) throws RemoteException;

    boolean disable(AttributionSource attributionSource, boolean persist) throws RemoteException;

    abstract class Stub extends Binder implements IBluetoothManager {
        public static IBluetoothManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
