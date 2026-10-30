package io.github.octoclonius.libreauto.projection.usb

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager

internal fun attachedAccessory(context: Context): UsbAccessory? =
    context.getSystemService(UsbManager::class.java).accessoryList?.firstOrNull()
