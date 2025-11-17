package com.gbaoperator.plugin.usb

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gbaoperator.plugin.data.CartridgeInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class GBOperatorInterfaceTest {
    
    private lateinit var context: Context
    private lateinit var gbOperator: GBOperatorInterface
    
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        gbOperator = GBOperatorInterface(context)
    }
    
    @After
    fun tearDown() {
        runBlocking {
            gbOperator.disconnect()
        }
    }
    
    @Test
    fun testDeviceConnection() = runBlocking {
        // Note: This test requires an actual GB Operator device connected
        val isConnected = gbOperator.connect()
        
        // If device is not available, test should pass with warning
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Skipping connection test.")
            return@runBlocking
        }
        
        assertTrue("Device should be connected", isConnected)
        assertTrue("Device should be available", gbOperator.isDeviceAvailable())
    }
    
    @Test
    fun testCartridgeDetection() = runBlocking {
        val isConnected = gbOperator.connect()
        
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Skipping cartridge test.")
            return@runBlocking
        }
        
        val cartridgeInfo = gbOperator.getCartridgeInfo()
        
        // If no cartridge is inserted, this is expected behavior
        if (cartridgeInfo == null) {
            println("INFO: No cartridge detected. Insert a GBA cartridge to test detection.")
            return@runBlocking
        }
        
        // If cartridge is detected, validate the information
        assertNotNull("Cartridge info should not be null", cartridgeInfo)
        assertNotEquals("Title should not be empty", "", cartridgeInfo.title.trim())
        assertTrue("ROM size should be positive", cartridgeInfo.romSize > 0)
        assertNotEquals("Game code should not be empty", "", cartridgeInfo.gameCode.trim())
    }
    
    @Test
    fun testRomReading() = runBlocking {
        val isConnected = gbOperator.connect()
        
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Skipping ROM test.")
            return@runBlocking
        }
        
        val cartridgeInfo = gbOperator.getCartridgeInfo()
        
        if (cartridgeInfo == null) {
            println("INFO: No cartridge detected. Insert a GBA cartridge to test ROM reading.")
            return@runBlocking
        }
        
        // Test reading a small portion of ROM (first 1KB)
        val romData = gbOperator.readRom(0, 1024) { progress ->
            println("ROM read progress: ${(progress * 100).toInt()}%")
        }
        
        assertNotNull("ROM data should not be null", romData)
        assertEquals("ROM data should be 1KB", 1024, romData.size)
        
        // Check for GBA ROM header signature (should start with branch instruction)
        // Most GBA ROMs start with 0xEA (branch always) instruction
        assertTrue("ROM should have valid header", romData[0] == 0xEA.toByte() || romData[3] == 0xEA.toByte())
    }
    
    @Test
    fun testSaveDataOperations() = runBlocking {
        val isConnected = gbOperator.connect()
        
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Skipping save test.")
            return@runBlocking
        }
        
        val cartridgeInfo = gbOperator.getCartridgeInfo()
        
        if (cartridgeInfo == null || cartridgeInfo.saveSize == null) {
            println("INFO: No cartridge with save data detected. Insert a game with save support to test.")
            return@runBlocking
        }
        
        // Read current save data
        val originalSaveData = gbOperator.readSave { progress ->
            println("Save read progress: ${(progress * 100).toInt()}%")
        }
        
        assertNotNull("Save data should not be null", originalSaveData)
        assertEquals("Save data should match expected size", cartridgeInfo.saveSize, originalSaveData.size)
        
        // Create test save data (copy of original with minor modification)
        val testSaveData = originalSaveData.copyOf()
        if (testSaveData.isNotEmpty()) {
            testSaveData[0] = (testSaveData[0] + 1).toByte() // Modify first byte
        }
        
        // Write test save data
        val writeSuccess = gbOperator.writeSave(testSaveData) { progress ->
            println("Save write progress: ${(progress * 100).toInt()}%")
        }
        
        assertTrue("Save write should succeed", writeSuccess)
        
        // Read back the modified save data
        val modifiedSaveData = gbOperator.readSave { progress ->
            println("Save verification read progress: ${(progress * 100).toInt()}%")
        }
        
        assertNotNull("Modified save data should not be null", modifiedSaveData)
        assertArrayEquals("Save data should match what was written", testSaveData, modifiedSaveData)
        
        // Restore original save data
        val restoreSuccess = gbOperator.writeSave(originalSaveData) { progress ->
            println("Save restore progress: ${(progress * 100).toInt()}%")
        }
        
        assertTrue("Save restore should succeed", restoreSuccess)
    }
    
    @Test
    fun testErrorHandling() = runBlocking {
        val isConnected = gbOperator.connect()
        
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Testing error handling without device.")
            
            // Test operations without device should fail gracefully
            val cartridgeInfo = gbOperator.getCartridgeInfo()
            assertNull("Cartridge info should be null without device", cartridgeInfo)
            
            val romData = gbOperator.readRom(0, 1024) { _ -> }
            assertNull("ROM data should be null without device", romData)
            
            return@runBlocking
        }
        
        // Test invalid ROM read (beyond cartridge size)
        val cartridgeInfo = gbOperator.getCartridgeInfo()
        if (cartridgeInfo != null) {
            val invalidRomData = gbOperator.readRom(cartridgeInfo.romSize + 1000, 1024) { _ -> }
            assertNull("Invalid ROM read should return null", invalidRomData)
        }
        
        // Test invalid save write (if cartridge has no save support)
        if (cartridgeInfo?.saveSize == null) {
            val dummySaveData = ByteArray(1024)
            val writeResult = gbOperator.writeSave(dummySaveData) { _ -> }
            assertFalse("Save write should fail for cartridge without save support", writeResult)
        }
    }
    
    @Test
    fun testProgressCallbacks() = runBlocking {
        val isConnected = gbOperator.connect()
        
        if (!isConnected) {
            println("WARNING: GB Operator device not found. Skipping progress callback test.")
            return@runBlocking
        }
        
        val cartridgeInfo = gbOperator.getCartridgeInfo()
        
        if (cartridgeInfo == null) {
            println("INFO: No cartridge detected. Skipping progress callback test.")
            return@runBlocking
        }
        
        // Test ROM read progress callbacks
        val progressValues = mutableListOf<Float>()
        val romData = gbOperator.readRom(0, minOf(32768, cartridgeInfo.romSize)) { progress ->
            progressValues.add(progress)
        }
        
        assertNotNull("ROM data should not be null", romData)
        assertTrue("Should have received progress callbacks", progressValues.isNotEmpty())
        assertTrue("Progress should start at 0 or near 0", progressValues.first() <= 0.1f)
        assertTrue("Progress should end at 1.0 or near 1.0", progressValues.last() >= 0.9f)
        
        // Verify progress is monotonically increasing
        for (i in 1 until progressValues.size) {
            assertTrue(
                "Progress should be monotonically increasing", 
                progressValues[i] >= progressValues[i - 1]
            )
        }
    }
}

/**
 * Mock GB Operator Interface for unit testing without hardware
 */
class MockGBOperatorInterface(context: Context) : GBOperatorInterface(context) {
    
    private var mockConnected = false
    private var mockCartridge: CartridgeInfo? = null
    
    fun setMockCartridge(cartridge: CartridgeInfo?) {
        mockCartridge = cartridge
    }
    
    override suspend fun connect(): Boolean {
        mockConnected = true
        return true
    }
    
    override suspend fun disconnect() {
        mockConnected = false
    }
    
    override fun isDeviceAvailable(): Boolean = mockConnected
    
    override suspend fun getCartridgeInfo(): CartridgeInfo? = mockCartridge
    
    override suspend fun readRom(
        offset: Int,
        length: Int,
        progressCallback: (Float) -> Unit
    ): ByteArray? {
        if (!mockConnected || mockCartridge == null) return null
        
        val cartridge = mockCartridge!!
        if (offset >= cartridge.romSize) return null
        
        val actualLength = minOf(length, cartridge.romSize - offset)
        val data = ByteArray(actualLength)
        
        // Simulate reading with progress callbacks
        val chunkSize = 1024
        for (i in 0 until actualLength step chunkSize) {
            val currentChunk = minOf(chunkSize, actualLength - i)
            
            // Fill with mock data (pattern based on offset)
            for (j in 0 until currentChunk) {
                data[i + j] = ((offset + i + j) and 0xFF).toByte()
            }
            
            progressCallback((i + currentChunk).toFloat() / actualLength)
            
            // Simulate read delay
            kotlinx.coroutines.delay(10)
        }
        
        return data
    }
    
    override suspend fun readSave(progressCallback: (Float) -> Unit): ByteArray? {
        if (!mockConnected || mockCartridge?.saveSize == null) return null
        
        val saveSize = mockCartridge!!.saveSize!!
        val data = ByteArray(saveSize)
        
        // Fill with mock save data
        for (i in data.indices) {
            data[i] = (i and 0xFF).toByte()
        }
        
        progressCallback(1.0f)
        return data
    }
    
    override suspend fun writeSave(
        data: ByteArray,
        progressCallback: (Float) -> Unit
    ): Boolean {
        if (!mockConnected || mockCartridge?.saveSize == null) return false
        
        val expectedSize = mockCartridge!!.saveSize!!
        if (data.size != expectedSize) return false
        
        progressCallback(1.0f)
        return true
    }
}