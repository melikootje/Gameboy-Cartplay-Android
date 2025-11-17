package com.gbaoperator.plugin.core

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.gbaoperator.plugin.data.CartridgeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.charset.StandardCharsets

open class GBOperatorInterface(private val context: Context) {
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var device: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null
    protected var detectedDeviceType: DeviceType = DeviceType.UNKNOWN

    enum class DeviceType(val displayName: String, val maxSpeed: Int) {
        GBXCART_RW("GBxCart RW", 200_000),
        GBFLASH("GBFlash", 550_000),
        JOEY_JR("Joey Jr", 150_000),
        EPILOGUE_GB_OPERATOR("Epilogue GB Operator", 400_000),
        UNKNOWN("Unknown Device", 100_000)
    }

    companion object {
        private const val FLASHGBX_VID = 0x1a86
        private const val FLASHGBX_PID_CH340 = 0x7523
        private const val FLASHGBX_PID_CH341 = 0x5523
        private const val JOEY_VID = 0x16D0
        private const val JOEY_PID = 0x0F07
        private const val EPILOGUE_VID = 0x16D0
        private const val EPILOGUE_PID = 0x0F3B
        private const val TIMEOUT_MS = 5000
        private const val CMD_RESET = "RESET\n"
        private const val RESPONSE_GBXCART = "GBxCart_RW"
        private const val RESPONSE_GBFLASH = "GBFlash"
        private const val RESPONSE_JOEY = "Joey Jr"
        private const val RESPONSE_EPILOGUE = "GB Operator"
    }

    open suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            device = findCompatibleDevice()
            if (device == null) {
                Timber.e("No FlashGBX compatible device found")
                return@withContext false
            }

            usbInterface = findSerialInterface(device!!)
            if (usbInterface == null) {
                Timber.e("Could not find serial interface")
                return@withContext false
            }

            if (!usbManager.hasPermission(device)) {
                // In a real app, you would request permission here
                Timber.e("No permission to access USB device")
                return@withContext false
            }

            connection = usbManager.openDevice(device)
            if (connection == null) {
                Timber.e("Failed to open USB connection")
                return@withContext false
            }

            if (!connection!!.claimInterface(usbInterface, true)) {
                Timber.e("Failed to claim USB interface")
                disconnect()
                return@withContext false
            }

            setEndpoints(usbInterface!!)

            // Basic serial port configuration for CH340
            connection?.controlTransfer(0x40, 0xA1, 0, 0, null, 0, TIMEOUT_MS) // SET BAUDRATE, etc.

            delay(100)

            val response = sendCommand(CMD_RESET)
            detectedDeviceType = when {
                response.contains(RESPONSE_GBXCART) -> DeviceType.GBXCART_RW
                response.contains(RESPONSE_GBFLASH) -> DeviceType.GBFLASH
                response.contains(RESPONSE_JOEY) -> DeviceType.JOEY_JR
                response.contains(RESPONSE_EPILOGUE) -> DeviceType.EPILOGUE_GB_OPERATOR
                else -> DeviceType.UNKNOWN
            }

            if (detectedDeviceType == DeviceType.UNKNOWN) {
                Timber.e("Device not responding correctly: $response")
                disconnect()
                return@withContext false
            }

            Timber.i("Successfully connected to ${detectedDeviceType.displayName}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error connecting to GBxCart RW")
            disconnect()
            false
        }
    }

    open fun disconnect() {
        try {
            connection?.releaseInterface(usbInterface)
            connection?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from GBxCart RW")
        } finally {
            connection = null
            device = null
        }
    }

    fun isDeviceAvailable(): Boolean = connection != null && device != null

    open suspend fun getCartridgeInfo(): CartridgeInfo? {
        // Dummy implementation. In a real scenario, you would send a command to the device to get this info.
        return CartridgeInfo("Pokemon Emerald", "BPEE", "01", 1, 32 * 1024 * 1024, 128 * 1024)
    }

    open suspend fun readRom(offset: Int, length: Int, progressCallback: (Float) -> Unit): ByteArray? {
        // This is where you would implement the logic to read the ROM from the device
        // using the readEndpoint and writeEndpoint. For now, we'll return a dummy byte array.
        progressCallback(1.0f)
        return ByteArray(length)
    }

    open suspend fun readSave(progressCallback: (Float) -> Unit): ByteArray? {
        // Dummy implementation
        progressCallback(1.0f)
        return ByteArray(128 * 1024)
    }

    open suspend fun writeSave(data: ByteArray, progressCallback: (Float) -> Unit): Boolean {
        // Dummy implementation
        progressCallback(1.0f)
        return true
    }

    open fun getDeviceType(): DeviceType = detectedDeviceType

    open fun getDeviceInfo(): String? {
        return if (isDeviceAvailable()) {
            when (detectedDeviceType) {
                DeviceType.GBXCART_RW -> "GBxCart RW - Original FlashGBX compatible device"
                DeviceType.GBFLASH -> "GBFlash - High-speed cartridge reader (up to 550 KB/s)"
                DeviceType.JOEY_JR -> "Joey Jr - BennVenn\'s cartridge interface"
                DeviceType.EPILOGUE_GB_OPERATOR -> "Epilogue GB Operator - Enhanced performance and features"
                DeviceType.UNKNOWN -> "Unknown FlashGBX compatible device"
            }
        } else {
            null
        }
    }

    private fun findCompatibleDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        return deviceList.values.find { isCompatibleDevice(it) }
    }

    private fun isCompatibleDevice(device: UsbDevice): Boolean {
        val isFlashGBXDevice = device.vendorId == FLASHGBX_VID &&
            (device.productId == FLASHGBX_PID_CH340 || device.productId == FLASHGBX_PID_CH341)
        val isJoeyDevice = device.vendorId == JOEY_VID && device.productId == JOEY_PID
        val isEpilogueDevice = device.vendorId == EPILOGUE_VID && device.productId == EPILOGUE_PID
        return isFlashGBXDevice || isJoeyDevice || isEpilogueDevice
    }

    private fun findSerialInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA || iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                return iface
            }
        }
        return null
    }

    private fun setEndpoints(iface: UsbInterface) {
        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    readEndpoint = endpoint
                } else {
                    writeEndpoint = endpoint
                }
            }
        }
    }

    private suspend fun sendCommand(command: String): String = withContext(Dispatchers.IO) {
        val commandBytes = command.toByteArray(StandardCharsets.UTF_8)
        val bytesSent = connection?.bulkTransfer(writeEndpoint, commandBytes, commandBytes.size, TIMEOUT_MS) ?: -1
        if (bytesSent < 0) {
            Timber.e("Failed to send command")
            return@withContext ""
        }
        val responseBuffer = ByteArray(64)
        val bytesRead = connection?.bulkTransfer(readEndpoint, responseBuffer, responseBuffer.size, TIMEOUT_MS) ?: -1
        if (bytesRead > 0) {
            String(responseBuffer, 0, bytesRead, StandardCharsets.UTF_8)
        } else {
            ""
        }
    }
}
