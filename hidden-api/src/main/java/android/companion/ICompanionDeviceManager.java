package android.companion;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface ICompanionDeviceManager extends IInterface {
    abstract class Stub extends Binder implements ICompanionDeviceManager {
        public static ICompanionDeviceManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
