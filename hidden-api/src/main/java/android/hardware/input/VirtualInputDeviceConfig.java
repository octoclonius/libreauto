package android.hardware.input;

public abstract class VirtualInputDeviceConfig {
    public abstract static class Builder<T extends Builder<T>> {
        public T setAssociatedDisplayId(int displayId) {
            throw new RuntimeException("Stub!");
        }

        public T setInputDeviceName(String deviceName) {
            throw new RuntimeException("Stub!");
        }
    }
}
