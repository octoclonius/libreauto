package android.companion.virtual;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IVirtualDeviceManager extends IInterface {
    abstract class Stub extends Binder implements IVirtualDeviceManager {
        public static IVirtualDeviceManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
