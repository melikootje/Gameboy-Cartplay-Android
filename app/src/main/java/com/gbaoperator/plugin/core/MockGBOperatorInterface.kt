package com.gbaoperator.plugin.core

import android.content.Context
import com.gbaoperator.plugin.data.CartridgeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Mock implementation of GBOperatorInterface for testing without physical hardware
 * Simulates a connected GB cartridge reader with sample ROM data
 */
class MockGBOperatorInterface(context: Context) : GBOperatorInterface(context) {

    private var isMockConnected = false
    private val mockDeviceType = DeviceType.GBFLASH  // Changed to GBFlash for high-speed simulation

    // Real GBA ROM data (will be loaded from file)
    private var mockRomData: ByteArray = ByteArray(0)
    private val mockRomPath = "/sdcard/Download/rom.gba"  // Path for user-provided ROM
    private val mockSaveData = ByteArray(128 * 1024) { 0xFF.toByte() } // 128KB SRAM

    override suspend fun connect(): Boolean {
        Timber.i("Mock: Attempting to connect to virtual GB Operator device...")
        delay(500) // Simulate connection delay

        // Load real ROM if available
        loadRomFromFile()

        isMockConnected = true
        detectedDeviceType = mockDeviceType
        Timber.i("Mock: Successfully connected to virtual ${mockDeviceType.displayName}")
        Timber.i("Mock: ROM size: ${mockRomData.size} bytes (${mockRomData.size / (1024 * 1024)} MB)")
        return true
    }

    private suspend fun loadRomFromFile() = withContext(Dispatchers.IO) {
        try {
            val romFile = java.io.File(mockRomPath)
            if (romFile.exists() && romFile.canRead()) {
                mockRomData = romFile.readBytes()
                Timber.i("Mock: Loaded real ROM from $mockRomPath (${mockRomData.size} bytes)")
            } else {
                Timber.w("Mock: ROM file not found at $mockRomPath, using generated mock data")
                mockRomData = generateMockRomData()
            }
        } catch (e: Exception) {
            Timber.e(e, "Mock: Failed to load ROM from file, using generated mock data")
            mockRomData = generateMockRomData()
        }
    }

    override fun disconnect() {
        Timber.i("Mock: Disconnecting from virtual GB Operator device")
        isMockConnected = false
    }

    override suspend fun getCartridgeInfo(): CartridgeInfo? {
        if (!isMockConnected) return null

        Timber.i("Mock: Reading cartridge information...")
        delay(300) // Simulate read delay

        // Parse real ROM header (GBA format)
        val title = if (mockRomData.size >= 0xAC) {
            String(mockRomData.copyOfRange(0xA0, 0xAC), Charsets.US_ASCII).trim()
        } else {
            "UNKNOWN"
        }

        val gameCode = if (mockRomData.size >= 0xB0) {
            String(mockRomData.copyOfRange(0xAC, 0xB0), Charsets.US_ASCII).trim()
        } else {
            "????"
        }

        val makerCode = if (mockRomData.size >= 0xB2) {
            String(mockRomData.copyOfRange(0xB0, 0xB2), Charsets.US_ASCII).trim()
        } else {
            "??"
        }

        val version = if (mockRomData.size > 0xBC) {
            mockRomData[0xBC].toInt() and 0xFF
        } else {
            0
        }

        val romSize = mockRomData.size
        val saveSize = 128 * 1024 // 128KB default

        return CartridgeInfo(
            title = title,
            gameCode = gameCode,
            makerCode = makerCode,
            version = version,
            romSize = romSize,
            saveSize = saveSize
        ).also {
            Timber.i("Mock: Detected cartridge - ${it.title} (${it.gameCode})")
        }
    }

    override suspend fun readRom(offset: Int, length: Int, progressCallback: (Float) -> Unit): ByteArray? {
        if (!isMockConnected) return null

        Timber.i("Mock: Reading ROM data (offset=$offset, length=$length)")

        // Simulate reading with progress updates (faster for GBFlash)
        val chunkSize = 128 * 1024 // 128KB chunks (larger for GBFlash speed)
        val totalChunks = (length + chunkSize - 1) / chunkSize

        for (i in 0 until totalChunks) {
            delay(20) // Faster delay for GBFlash (550 KB/s)
            val progress = (i + 1).toFloat() / totalChunks
            progressCallback(progress)
        }

        // Return actual ROM data from loaded file
        val data = ByteArray(minOf(length, mockRomData.size - offset).coerceAtLeast(0))
        if (offset < mockRomData.size && data.isNotEmpty()) {
            System.arraycopy(mockRomData, offset, data, 0, data.size)
        }

        Timber.i("Mock: Successfully read ${data.size} bytes of ROM data from offset $offset")
        return data
    }

    override suspend fun readSave(progressCallback: (Float) -> Unit): ByteArray? {
        if (!isMockConnected) return null

        Timber.i("Mock: Reading save data...")

        // Simulate reading with progress
        for (i in 1..10) {
            delay(100)
            progressCallback(i / 10f)
        }

        Timber.i("Mock: Successfully read ${mockSaveData.size} bytes of save data")
        return mockSaveData.clone()
    }

    override suspend fun writeSave(data: ByteArray, progressCallback: (Float) -> Unit): Boolean {
        if (!isMockConnected) return false

        Timber.i("Mock: Writing ${data.size} bytes of save data...")

        // Simulate writing with progress
        for (i in 1..10) {
            delay(100)
            progressCallback(i / 10f)
        }

        // Update mock save data
        System.arraycopy(data, 0, mockSaveData, 0, minOf(data.size, mockSaveData.size))

        Timber.i("Mock: Successfully wrote save data")
        return true
    }

    override fun getDeviceType(): DeviceType = mockDeviceType

    override fun getDeviceInfo(): String {
        val cartInfo = if (isMockConnected && mockRomData.isNotEmpty()) {
            val title = if (mockRomData.size >= 0xAC) {
                String(mockRomData.copyOfRange(0xA0, 0xAC), Charsets.US_ASCII).trim()
            } else "UNKNOWN"
            val gameCode = if (mockRomData.size >= 0xB0) {
                String(mockRomData.copyOfRange(0xAC, 0xB0), Charsets.US_ASCII).trim()
            } else "????"
            """
            
            Cartridge Info:
            - Title: $title
            - Game Code: $gameCode
            - ROM Size: ${mockRomData.size / (1024 * 1024)} MB
            - Save Type: Flash 128KB
            - ROM Source: ${if (java.io.File(mockRomPath).exists()) "Real ROM file" else "Generated mock"}
            """.trimIndent()
        } else {
            "\n\nNo cartridge detected"
        }

        return """
            Mock GB Operator Device
            Type: ${mockDeviceType.displayName}
            Max Speed: ${mockDeviceType.maxSpeed / 1000} KB/s
            Status: ${if (isMockConnected) "Connected" else "Disconnected"}$cartInfo
        """.trimIndent()
    }

    private fun generateMockRomData(): ByteArray {
        // Create a minimal GBA ROM header
        return ByteArray(192).apply {
            // Entry point (ARM branch instruction)
            this[0] = 0x2E
            this[1] = 0x00.toByte()
            this[2] = 0x00
            this[3] = 0xEA.toByte()

            // Nintendo logo (compressed, required for GBA)
            val nintendoLogo = byteArrayOf(
                0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
                0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A, 0x84.toByte(), 0xE4.toByte(), 0x09, 0xAD.toByte()
            )
            System.arraycopy(nintendoLogo, 0, this, 0x04, nintendoLogo.size)

            // Game Title (offset 0xA0, 12 bytes)
            val title = "POKEMON FIRE".toByteArray()
            System.arraycopy(title, 0, this, 0xA0, title.size)

            // Game Code (offset 0xAC, 4 bytes)
            val gameCode = "BPRE".toByteArray()
            System.arraycopy(gameCode, 0, this, 0xAC, gameCode.size)

            // Maker Code (offset 0xB0, 2 bytes) - "01" = Nintendo
            this[0xB0] = '0'.code.toByte()
            this[0xB1] = '1'.code.toByte()

            // Fixed value (must be 0x96)
            this[0xB2] = 0x96.toByte()

            // Main unit code
            this[0xB3] = 0x00

            // Device type
            this[0xB4] = 0x00

            // Reserved area (7 bytes)
            for (i in 0xB5..0xBB) {
                this[i] = 0x00
            }

            // Software version
            this[0xBC] = 0x00

            // Complement check (will be calculated)
            this[0xBD] = 0x00

            // Reserved (2 bytes)
            this[0xBE] = 0x00
            this[0xBF] = 0x00
        }
    }
}

