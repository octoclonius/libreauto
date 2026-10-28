package io.github.octoclonius.libreauto.privileged;

interface IPhoneBluetoothService {
    void grantPermission() = 1;

    boolean turnOn() = 2;

    @nullable String getAddress() = 3;

    boolean pair(String address) = 4;

    boolean cancelPairing(String address) = 5;

    boolean removePairing(String address) = 6;

    boolean setPin(String address, String pin) = 7;

    boolean confirmPairing(String address, boolean isConfirmed) = 8;

    boolean allowHandsFree(String address) = 9;

    boolean connectHandsFree(String address) = 10;
}
