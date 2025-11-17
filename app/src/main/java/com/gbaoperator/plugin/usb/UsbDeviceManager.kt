package com.gbaoperator.plugin.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import timber.log.Timber

class UsbDeviceManager(private val context: Context) {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    companion object {
        // FlashGBX/GBxCart RW (CH340/CH341 serial chips)
        private const val FLASHGBX_VID = 0x1a86
        private const val FLASHGBX_PID_CH340 = 0x7523
        private const val FLASHGBX_PID_CH341 = 0x5523

        // Joey Jr
        private const val JOEY_VID = 0x16D0
        private const val JOEY_PID = 0x0F07

        // Epilogue GB Operator
        private const val EPILOGUE_VID = 0x16D0
        private const val EPILOGUE_PID = 0x0F3B
    }

    fun findGBOperatorDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        Timber.d("Searching for compatible cartridge reader among ${deviceList.size} connected devices")

        return deviceList.values.firstOrNull { device ->
            val isFlashGBX = device.vendorId == FLASHGBX_VID &&
                (device.productId == FLASHGBX_PID_CH340 || device.productId == FLASHGBX_PID_CH341)
            val isJoey = device.vendorId == JOEY_VID && device.productId == JOEY_PID
            val isEpilogue = device.vendorId == EPILOGUE_VID && device.productId == EPILOGUE_PID

            val isCompatible = isFlashGBX || isJoey || isEpilogue

            if (isCompatible) {
                val deviceType = when {
                    isFlashGBX -> "FlashGBX/GBxCart RW"
                    isJoey -> "Joey Jr"
                    isEpilogue -> "Epilogue GB Operator"
                    else -> "Unknown"
                }
                Timber.i("Found compatible device: $deviceType (${device.deviceName})")
            }

            isCompatible
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
}
