package com.gbaoperator.plugin.integration

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.gbaoperator.plugin.core.GBOperatorInterface
import com.gbaoperator.plugin.data.CartridgeInfo
import com.gbaoperator.plugin.emulator.*
import com.gbaoperator.plugin.usb.UsbDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.io.File

/**
 * Service that provides integration between emulators and GB Operator
 * Acts as a bridge for ROM loading, save synchronization, and device management
 */
class EmulatorIntegrationService : Service() {

    private val binder = LocalBinder()
    private var gbOperator: GBOperatorInterface? = null
    private var usbDeviceManager: UsbDeviceManager? = null
    private val emulatorAdapters = mutableMapOf<String, EmulatorAdapter>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val ACTION_LOAD_ROM = "com.gbaoperator.plugin.LOAD_ROM"
        const val ACTION_SYNC_SAVE = "com.gbaoperator.plugin.SYNC_SAVE"
        const val ACTION_LOAD_SAVE = "com.gbaoperator.plugin.LOAD_SAVE"
        const val ACTION_GET_CARTRIDGE_INFO = "com.gbaoperator.plugin.GET_CARTRIDGE_INFO"
        
        const val EXTRA_EMULATOR_PACKAGE = "emulator_package"
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_SAVE_PATH = "save_path"
        const val EXTRA_CARTRIDGE_INFO = "cartridge_info"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): EmulatorIntegrationService = this@EmulatorIntegrationService
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("EmulatorIntegrationService created")
        
        usbDeviceManager = UsbDeviceManager(this)
        initializeEmulatorAdapters()
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        val emulatorPackage = intent.getStringExtra(EXTRA_EMULATOR_PACKAGE)
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)

        when (intent.action) {
            ACTION_LOAD_ROM -> {
                serviceScope.launch {
                    if (!romPath.isNullOrBlank()) {
                        // Launch emulator using provided rom path (pre-dumped)
                        launchWithRomPath(emulatorPackage, romPath)
                    } else {
                        loadRomFromCartridge(emulatorPackage)
                    }
                }
            }
            ACTION_SYNC_SAVE -> {
                val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
                serviceScope.launch {
                    syncSaveToCartridge(emulatorPackage, savePath)
                }
            }
            ACTION_LOAD_SAVE -> {
                val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
                serviceScope.launch {
                    loadSaveFromCartridge(emulatorPackage, savePath)
                }
            }
            ACTION_GET_CARTRIDGE_INFO -> {
                serviceScope.launch {
                    getCartridgeInfo()
                }
            }
        }
    }
    
    /**
     * Initialize adapters for supported emulators
     */
    private fun initializeEmulatorAdapters() {
        // mGBA adapter
        emulatorAdapters["com.endrift.mgba"] = MGBAAdapter()
        
        // MyBoy! adapter
        emulatorAdapters["com.fastemulator.gba"] = MyBoyAdapter()
        emulatorAdapters["com.fastemulator.gbafree"] = MyBoyAdapter()
        
        // RetroArch adapter
        emulatorAdapters["com.retroarch"] = RetroArchAdapter()

        // Pizza Boy GBA (paid/free)
        emulatorAdapters["it.dbtecno.pizzaboygba"] = PizzaBoyGBAAdapter()
        emulatorAdapters["it.dbtecno.pizzaboygbafree"] = PizzaBoyGBAFreeAdapter()

        // John GBA (paid/lite)
        emulatorAdapters["com.johnemulators.johngba"] = JohnGBAAdapter()
        emulatorAdapters["com.johnemulators.johngbalite"] = JohnGBALiteAdapter()

        // Lemuroid
        emulatorAdapters["com.swordfish.lemuroid"] = LemuroidAdapter()

        // My OldBoy! (GBC) paid/free
        emulatorAdapters["com.fastemulator.gbc"] = MyOldBoyAdapter()
        emulatorAdapters["com.fastemulator.gbcfree"] = MyOldBoyFreeAdapter()

        // Pizza Boy GBC
        emulatorAdapters["it.dbtecno.pizzaboygbc"] = PizzaBoyGBCAdapter()

        Timber.i("Initialized ${emulatorAdapters.size} emulator adapters")
    }
    
    /**
     * Connect to GB Operator device
     */
    suspend fun connectToGBOperator(): Boolean {
        val usbDevice = usbDeviceManager?.findGBOperatorDevice() ?: run {
            Timber.e("No GB Operator device found")
            return false
        }
        
        gbOperator = GBOperatorInterface(this)

        return gbOperator?.connect() ?: false
    }
    
    /**
     * Disconnect from GB Operator device
     */
    fun disconnectFromGBOperator() {
        gbOperator?.disconnect()
        gbOperator = null
    }
    
    /**
     * Load ROM from cartridge and launch in emulator
     */
    private suspend fun loadRomFromCartridge(emulatorPackage: String?) {
        try {
            if (!ensureGBOperatorConnected()) return
            
            val cartridgeInfo = gbOperator?.getCartridgeInfo()
            if (cartridgeInfo == null) {
                Timber.e("No cartridge detected")
                sendErrorBroadcast("No cartridge detected")
                return
            }
            
            Timber.i("Reading ROM from cartridge: ${cartridgeInfo.title}")
            
            val romData = gbOperator?.readRom(0, cartridgeInfo.romSize) { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Reading ROM...")
            }
            
            if (romData == null) {
                Timber.e("Failed to read ROM data")
                sendErrorBroadcast("Failed to read ROM data")
                return
            }
            
            // Save ROM to temporary file
            val romFile = createTempRomFile(cartridgeInfo.title, romData)
            
            // Launch emulator with ROM
            val adapter = getEmulatorAdapter(emulatorPackage)
            if (adapter != null) {
                val success = adapter.loadRom(this@EmulatorIntegrationService, romFile.absolutePath)
                if (success) {
                    sendSuccessBroadcast("ROM loaded successfully", mapOf(
                        EXTRA_ROM_PATH to romFile.absolutePath,
                        EXTRA_CARTRIDGE_INFO to cartridgeInfo
                    ))
                } else {
                    sendErrorBroadcast("Failed to launch emulator")
                }
            } else {
                sendErrorBroadcast("Emulator not supported: $emulatorPackage")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error loading ROM from cartridge")
            sendErrorBroadcast("Error: ${e.message}")
        }
    }

    private suspend fun launchWithRomPath(emulatorPackage: String?, romPath: String) {
        try {
            val adapter = getEmulatorAdapter(emulatorPackage) ?: run {
                sendErrorBroadcast("Emulator not supported: $emulatorPackage")
                return
            }
            val success = adapter.loadRom(this@EmulatorIntegrationService, romPath)
            if (success) {
                sendSuccessBroadcast("ROM launched successfully", mapOf(EXTRA_ROM_PATH to romPath))
            } else {
                sendErrorBroadcast("Failed to launch emulator with ROM: $romPath")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error launching emulator with ROM path")
            sendErrorBroadcast("Error: ${e.message}")
        }
    }
    
    /**
     * Synchronize save data from emulator to cartridge
     */
    private suspend fun syncSaveToCartridge(emulatorPackage: String?, savePath: String?) {
        try {
            if (!ensureGBOperatorConnected()) return
            
            val adapter = getEmulatorAdapter(emulatorPackage)
            if (adapter == null) {
                sendErrorBroadcast("Emulator not supported: $emulatorPackage")
                return
            }
            
            val saveData = adapter.getSaveData(this, savePath) ?: run {
                sendErrorBroadcast("Failed to get save data from emulator")
                return
            }
            
            Timber.i("Writing ${saveData.size} bytes of save data to cartridge")
            
            val success = gbOperator?.writeSave(saveData) { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Writing save...")
            } ?: false
            if (success) {
                sendSuccessBroadcast("Save data synchronized to cartridge")
            } else {
                sendErrorBroadcast("Failed to write save data to cartridge")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error syncing save to cartridge")
            sendErrorBroadcast("Error: ${e.message}")
        }
    }
    
    /**
     * Load save data from cartridge to emulator
     */
    private suspend fun loadSaveFromCartridge(emulatorPackage: String?, savePath: String?) {
        try {
            if (!ensureGBOperatorConnected()) return
            
            val saveData = gbOperator?.readSave { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Reading save...")
            }
            if (saveData == null) {
                sendErrorBroadcast("Failed to read save data from cartridge")
                return
            }
            
            if (saveData.isEmpty()) {
                sendErrorBroadcast("No save data found on cartridge")
                return
            }
            
            val adapter = getEmulatorAdapter(emulatorPackage)
            if (adapter == null) {
                sendErrorBroadcast("Emulator not supported: $emulatorPackage")
                return
            }
            
            Timber.i("Loading ${saveData.size} bytes of save data from cartridge")
            
            val success = adapter.setSaveData(this, savePath, saveData)
            if (success) {
                sendSuccessBroadcast("Save data loaded from cartridge")
            } else {
                sendErrorBroadcast("Failed to load save data into emulator")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error loading save from cartridge")
            sendErrorBroadcast("Error: ${e.message}")
        }
    }
    
    /**
     * Get cartridge information
     */
    private suspend fun getCartridgeInfo() {
        try {
            if (!ensureGBOperatorConnected()) return
            
            val cartridgeInfo = gbOperator?.getCartridgeInfo()
            if (cartridgeInfo != null) {
                sendSuccessBroadcast("Cartridge info retrieved", mapOf(
                    EXTRA_CARTRIDGE_INFO to cartridgeInfo
                ))
            } else {
                sendErrorBroadcast("No cartridge detected or failed to read cartridge info")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error getting cartridge info")
            sendErrorBroadcast("Error: ${e.message}")
        }
    }
    
    /**
     * Ensure GB Operator is connected
     */
    private suspend fun ensureGBOperatorConnected(): Boolean {
        if (gbOperator == null) {
            if (!connectToGBOperator()) {
                sendErrorBroadcast("Failed to connect to GB Operator")
                return false
            }
        }
        return true
    }
    
    /**
     * Get appropriate emulator adapter
     */
    private fun getEmulatorAdapter(emulatorPackage: String?): EmulatorAdapter? {
        if (emulatorPackage == null) {
            // Try to detect running emulator
            return detectRunningEmulator()
        }
        
        return emulatorAdapters[emulatorPackage]
    }
    
    /**
     * Detect currently running emulator
     */
    private fun detectRunningEmulator(): EmulatorAdapter? {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return null

        for (processInfo in runningProcesses) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (pkg in processInfo.pkgList) {
                    if (emulatorAdapters.containsKey(pkg)) {
                        Timber.i("Detected running emulator: $pkg")
                        return emulatorAdapters[pkg]
                    }
                }
            }
        }

        Timber.w("No running emulator detected")
        sendErrorBroadcast("No running emulator detected")
        return null
    }
    
    /**
     * Create temporary ROM file
     */
    private fun createTempRomFile(title: String, romData: ByteArray): File {
        val fileName = "${title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.gba"
        val romFile = File(cacheDir, fileName)
        romFile.writeBytes(romData)
        return romFile
    }
    
    /**
     * Send success broadcast
     */
    private fun sendSuccessBroadcast(message: String, extras: Map<String, Any> = emptyMap()) {
        val intent = Intent("com.gbaoperator.plugin.SUCCESS").apply {
            putExtra("message", message)
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is CartridgeInfo -> {
                        putExtra("${key}_title", value.title)
                        putExtra("${key}_game_code", value.gameCode)
                        putExtra("${key}_rom_size", value.romSize)
                        putExtra("${key}_save_size", value.saveSize ?: 0)
                    }
                }
            }
        }
        sendBroadcast(intent)
    }
    
    /**
     * Send error broadcast
     */
    private fun sendErrorBroadcast(message: String) {
        val intent = Intent("com.gbaoperator.plugin.ERROR").apply {
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Send progress broadcast
     */
    private fun sendProgressBroadcast(progress: Int, total: Int, message: String) {
        val intent = Intent("com.gbaoperator.plugin.PROGRESS").apply {
            putExtra("progress", progress)
            putExtra("total", total)
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Dump ROM to file if not already cached
     */
    suspend fun dumpRomToFile(cartridgeInfo: CartridgeInfo): Boolean {
        return try {
            if (!ensureGBOperatorConnected()) return false
            
            val romFile = getRomFile(cartridgeInfo)
            if (romFile.exists()) {
                Timber.i("ROM already cached: ${romFile.absolutePath}")
                return true
            }
            
            Timber.i("Dumping ROM from cartridge: ${cartridgeInfo.title}")
            
            val romData = gbOperator?.readRom(0, cartridgeInfo.romSize) { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Dumping ROM...")
            }
            
            if (romData != null) {
                romFile.parentFile?.mkdirs()
                romFile.writeBytes(romData)
                Timber.i("ROM dumped to: ${romFile.absolutePath}")
                true
            } else {
                Timber.e("Failed to read ROM data")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to dump ROM")
            false
        }
    }
    
    /**
     * Get ROM file path for a cartridge
     */
    fun getRomPath(cartridgeInfo: CartridgeInfo): String? {
        val romFile = getRomFile(cartridgeInfo)
        return if (romFile.exists()) romFile.absolutePath else null
    }
    
    /**
     * Get ROM file for a cartridge
     */
    private fun getRomFile(cartridgeInfo: CartridgeInfo): File {
        val romsDir = File(getExternalFilesDir("roms"), "dumped")
        val filename = "${cartridgeInfo.gameCode}_${cartridgeInfo.title}.gba"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(romsDir, filename)
    }
    
    /**
     * Launch emulator without video configuration (legacy method)
     */
    suspend fun launchEmulator(adapter: EmulatorAdapter, romPath: String): Boolean {
        return launchEmulatorWithConfig(adapter, romPath, null)
    }
    
    /**
     * Launch emulator with video configuration
     */
    suspend fun launchEmulatorWithConfig(
        adapter: EmulatorAdapter, 
        romPath: String, 
        videoConfig: VideoConfig?
    ): Boolean {
        return try {
            val success = adapter.loadRom(this, romPath, videoConfig)
            if (success) {
                Timber.i("Successfully launched ${adapter.getPackageName()} with ROM: $romPath")
                if (videoConfig != null) {
                    Timber.i("Video config applied: ${videoConfig.scaleMode.displayName}, ${videoConfig.shader.displayName}")
                }
            } else {
                Timber.e("Failed to launch ${adapter.getPackageName()}")
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "Error launching emulator: ${adapter.getPackageName()}")
            false
        }
    }
    
    /**
     * Backup save data from cartridge
     */
    suspend fun backupSaveData(cartridgeInfo: CartridgeInfo): Boolean {
        return try {
            if (!ensureGBOperatorConnected()) return false
            
            val saveData = gbOperator?.readSave { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Reading save data...")
            }
            
            if (saveData != null) {
                val saveFile = getSaveFile(cartridgeInfo)
                saveFile.parentFile?.mkdirs()
                saveFile.writeBytes(saveData)
                Timber.i("Save data backed up to: ${saveFile.absolutePath}")
                true
            } else {
                Timber.e("Failed to read save data")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to backup save data")
            false
        }
    }
    
    /**
     * Restore save data to cartridge
     */
    suspend fun restoreSaveData(cartridgeInfo: CartridgeInfo): Boolean {
        return try {
            if (!ensureGBOperatorConnected()) return false
            
            val saveFile = getSaveFile(cartridgeInfo)
            if (!saveFile.exists()) {
                Timber.e("Save file not found: ${saveFile.absolutePath}")
                return false
            }
            
            val saveData = saveFile.readBytes()
            val success = gbOperator?.writeSave(saveData) { progress ->
                sendProgressBroadcast((progress * 100).toInt(), 100, "Writing save data...")
            } ?: false
            
            if (success) {
                Timber.i("Save data restored from: ${saveFile.absolutePath}")
            } else {
                Timber.e("Failed to write save data to cartridge")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore save data")
            false
        }
    }
    
    /**
     * Get save file for a cartridge
     */
    private fun getSaveFile(cartridgeInfo: CartridgeInfo): File {
        val savesDir = File(getExternalFilesDir("saves"), "backups")
        val filename = "${cartridgeInfo.gameCode}_${cartridgeInfo.title}.sav"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(savesDir, filename)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnectFromGBOperator()
        serviceScope.cancel()
        Timber.i("EmulatorIntegrationService destroyed")
    }
}
