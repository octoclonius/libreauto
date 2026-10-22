package android.hardware.input;

import java.io.Closeable;

public class VirtualTouchscreen implements Closeable {
    public void sendTouchEvent(VirtualTouchEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void close() {
        throw new RuntimeException("Stub!");
    }
}
