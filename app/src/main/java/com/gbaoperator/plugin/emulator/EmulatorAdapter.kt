package com.gbaoperator.plugin.emulator

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/**
 * Video configuration data class
 */
data class VideoConfig(
    val scaleMode: ScaleMode = ScaleMode.INTEGER_4X,
    val shader: Shader = Shader.HQ3X,
    val maintainAspectRatio: Boolean = true,
    val frameRate: Int = 60
)

/**
 * Available scaling modes for GBA emulation
 */
enum class ScaleMode(val displayName: String, val factor: Int) {
    INTEGER_2X("2x Pixel Perfect", 2),
    INTEGER_3X("3x Pixel Perfect", 3),
    INTEGER_4X("4x Pixel Perfect", 4),
    INTEGER_5X("5x Pixel Perfect", 5),
    INTEGER_6X("6x Pixel Perfect", 6),
    FIT_SCREEN("Fit Screen", 0),
    STRETCH("Stretch", 0)
}

/**
 * Available shader filters for video enhancement
 */
enum class Shader(val displayName: String, val glslName: String) {
    NONE("No Filter", "none"),
    BILINEAR("Smooth", "bilinear"),
    HQ2X("HQ2x", "hq2x"),
    HQ3X("HQ3x", "hq3x"),
    HQ4X("HQ4x", "hq4x"),
    XBR_2X("xBR 2x", "xbr-2x"),
    XBR_3X("xBR 3x", "xbr-3x"),
    XBR_4X("xBR 4x", "xbr-4x"),
    SUPER_EAGLE("Super Eagle", "super-eagle"),
    SCALE2X("Scale2x", "scale2x")
}

/**
 * Device performance tiers for automatic optimization
 */
private enum class DeviceTier(val maxScale: Int, val recommendedShader: Shader) {
    HIGH_END(6, Shader.XBR_4X),      // Flagship phones
    MID_RANGE(4, Shader.HQ3X),       // Most Android devices
    LOW_END(3, Shader.HQ2X),         // Older devices
    BUDGET(2, Shader.NONE)           // Very low-end devices
}

/**
 * Abstract base class for emulator adapters
 * Each supported emulator has its own adapter implementation
 */
abstract class EmulatorAdapter {
    
    /**
     * Whether this emulator supports video configuration
     */
    abstract val supportsVideoConfig: Boolean
    
    /**
     * Launch emulator with specified ROM file and optional video configuration
     */
    abstract suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig? = null): Boolean
    
    /**
     * Get save data from emulator
     */
    abstract suspend fun getSaveData(context: Context, savePath: String?): ByteArray?
    
    /**
     * Set save data in emulator
     */
    abstract suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean
    
    /**
     * Check if emulator is installed
     */
    abstract fun isInstalled(context: Context): Boolean
    
    /**
     * Get emulator package name
     */
    abstract fun getPackageName(): String
    
    /**
     * Detect optimal video configuration based on device capabilities
     */
    protected fun detectOptimalVideoConfig(context: Context): VideoConfig {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val deviceTier = detectDeviceTier(context)
        
        // Calculate optimal integer scale for screen
        val maxScaleX = screenWidth / 240  // GBA native width
        val maxScaleY = screenHeight / 160  // GBA native height
        val optimalScale = minOf(maxScaleX, maxScaleY, deviceTier.maxScale)
        
        val scaleMode = when (optimalScale) {
            2 -> ScaleMode.INTEGER_2X
            3 -> ScaleMode.INTEGER_3X  
            4 -> ScaleMode.INTEGER_4X
            5 -> ScaleMode.INTEGER_5X
            6 -> ScaleMode.INTEGER_6X
            else -> ScaleMode.FIT_SCREEN
        }
        
        return VideoConfig(
            scaleMode = scaleMode,
            shader = deviceTier.recommendedShader,
            maintainAspectRatio = true
        )
    }
    
    /**
     * Detect device performance tier for automatic optimization
     */
    private fun detectDeviceTier(context: Context): DeviceTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        
        val totalRAM = memoryInfo.totalMem / (1024 * 1024 * 1024) // GB
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        return when {
            totalRAM >= 8 && cpuCores >= 8 -> DeviceTier.HIGH_END
            totalRAM >= 4 && cpuCores >= 6 -> DeviceTier.MID_RANGE  
            totalRAM >= 2 && cpuCores >= 4 -> DeviceTier.LOW_END
            else -> DeviceTier.BUDGET
        }
    }
}

/**
 * Helper to create appropriate URI for ROM file
 * - For files in app directories (cache, external files): use FileProvider
 * - For files in public storage (/sdcard/Download, etc.): use file:// URI directly
 */
private fun getRomUri(context: Context, romPath: String): android.net.Uri {
    // Handle explicit URIs
    if (romPath.startsWith("content://") || romPath.startsWith("file://")) {
        return android.net.Uri.parse(romPath)
    }
    val file = File(romPath)

    // Check if file is in app-controlled directories
    val appDirs = listOf(
        context.cacheDir,
        context.filesDir,
        context.getExternalFilesDir(null)
    ).filterNotNull()

    val isAppDir = appDirs.any { appDir ->
        try {
            file.canonicalPath.startsWith(appDir.canonicalPath)
        } catch (_: Exception) { false }
    }

    return if (isAppDir) {
        // Use FileProvider for app directories
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } else {
        // Use file:// for public storage (emulators can read these directly)
        android.net.Uri.fromFile(file)
    }
}

/**
 * Adapter for mGBA emulator
 */
class MGBAAdapter : EmulatorAdapter() {
    
    override val supportsVideoConfig: Boolean = true
    
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val config = videoConfig ?: detectOptimalVideoConfig(context)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.endrift.mgba")
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Add video configuration as extras for mGBA
                putExtra("scale_factor", config.scaleMode.factor)
                putExtra("shader", config.shader.glslName)
                putExtra("maintain_aspect_ratio", config.maintainAspectRatio)
                putExtra("frame_rate", config.frameRate)
                
                // Additional mGBA specific enhancements
                putExtra("bilinear_filtering", config.shader != Shader.NONE)
                putExtra("color_correction", true) // Enable GBA color correction
                putExtra("audio_buffer", 1024)     // Optimize audio latency
            }
            
            context.startActivity(intent)
            Timber.i("Launched mGBA with ROM: $romPath")
            true
        } catch (_: Exception) {
            Timber.e("Failed to launch mGBA")
            false
        }
    }
    
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return null
            if (saveFile.exists()) {
                saveFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get save data from mGBA")
            null
        }
    }
    
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return false
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(saveData)
            Timber.i("Wrote save data to: ${saveFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set save data for mGBA")
            false
        }
    }
    
    override fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.endrift.mgba", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getPackageName(): String = "com.endrift.mgba"
    
    private fun getSaveFile(context: Context, savePath: String?): File? {
        return if (savePath != null) {
            File(savePath)
        } else {
            // mGBA typically saves in Android/data/com.endrift.mgba/files/saves/
            val mgbaDir = File(context.getExternalFilesDir(null)?.parent, "com.endrift.mgba/files/saves")
            if (mgbaDir.exists()) {
                mgbaDir.listFiles()?.firstOrNull { it.extension == "sav" }
            } else {
                null
            }
        }
    }
}

/**
 * Adapter for MyBoy! GBA emulator
 */
class MyBoyAdapter : EmulatorAdapter() {
    
    override val supportsVideoConfig: Boolean = false // MyBoy! has limited config options
    
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
            Timber.i("Launched MyBoy! with ROM: $romPath")
            true
        } catch (_: Exception) {
            Timber.e("Failed to launch MyBoy!")
            false
        }
    }
    
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return null
            if (saveFile.exists()) {
                saveFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get save data from MyBoy!")
            null
        }
    }
    
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return false
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(saveData)
            Timber.i("Wrote save data to: ${saveFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set save data for MyBoy!")
            false
        }
    }
    
    override fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(getPackageName(), 0)
            true
        } catch (e: Exception) {
            // Try free version if paid version not found
            try {
                context.packageManager.getPackageInfo("com.fastemulator.gbafree", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
    
    override fun getPackageName(): String {
        // Return paid version by default, adapter handles fallback
        return "com.fastemulator.gba"
    }
    
    private fun getSaveFile(context: Context, savePath: String?): File? {
        return if (savePath != null) {
            File(savePath)
        } else {
            // MyBoy! saves in various locations, check common ones
            val commonPaths = listOf(
                "Android/data/com.fastemulator.gba/files",
                "Android/data/com.fastemulator.gbafree/files",
                "MyBoy"
            )
            
            for (path in commonPaths) {
                val saveDir = File(context.getExternalFilesDir(null)?.parent, path)
                if (saveDir.exists()) {
                    saveDir.listFiles()?.firstOrNull { 
                        it.extension == "sav" || it.extension == "sgm" 
                    }?.let { return it }
                }
            }
            null
        }
    }
}

/**
 * Adapter for RetroArch with mGBA core
 */
class RetroArchAdapter : EmulatorAdapter() {
    
    override val supportsVideoConfig: Boolean = true
    
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val config = videoConfig ?: detectOptimalVideoConfig(context)
            
            // RetroArch uses special intent format
            val intent = Intent().apply {
                action = "com.retroarch.LAUNCH"
                setPackage("com.retroarch")
                putExtra("ROM", romPath)
                putExtra("LIBRETRO", "/data/data/com.retroarch/cores/mgba_libretro_android.so")
                putExtra("CONFIGFILE", "/storage/emulated/0/RetroArch/retroarch.cfg")
                
                // RetroArch video configuration
                putExtra("video_scale_integer", config.scaleMode.factor > 0)
                putExtra("video_scale", config.scaleMode.factor)
                putExtra("video_shader", config.shader.glslName)
                putExtra("video_aspect_ratio_auto", config.maintainAspectRatio)
                putExtra("video_vsync", true)
                putExtra("video_smooth", config.shader != Shader.NONE)
                
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Timber.i("Launched RetroArch with ROM: $romPath")
            true
        } catch (_: Exception) {
            // Fallback to standard file opening
            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.retroarch")
                    val uri = getRomUri(context, romPath)
                    setDataAndType(uri, "application/octet-stream")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                Timber.e("Failed to launch RetroArch")
                false
            }
        }
    }
    
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return null
            if (saveFile.exists()) {
                saveFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get save data from RetroArch")
            null
        }
    }
    
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return false
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(saveData)
            Timber.i("Wrote save data to: ${saveFile.absolutePath}")
            true
        } catch (_: Exception) {
            Timber.e("Failed to set save data for RetroArch")
            false
        }
    }
    
    override fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.retroarch", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getPackageName(): String = "com.retroarch"
    
    private fun getSaveFile(context: Context, savePath: String?): File? {
        return if (savePath != null) {
            File(savePath)
        } else {
            // RetroArch saves in /storage/emulated/0/RetroArch/saves/
            val retroarchSavesDir = File("/storage/emulated/0/RetroArch/saves")
            if (retroarchSavesDir.exists()) {
                retroarchSavesDir.listFiles()?.firstOrNull { it.extension == "srm" }
            } else {
                null
            }
        }
    }
}

/**
 * Adapter for GBA.emu (nl.melikootje.gba.app)
 */
class GBAEmuAdapter : EmulatorAdapter() {

    override val supportsVideoConfig: Boolean = false

    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            Timber.i("Launched GBA.emu with ROM: $romPath")
            true
        } catch (_: Exception) {
            Timber.e("Failed to launch GBA.emu")
            false
        }
    }

    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return null
            if (saveFile.exists()) {
                saveFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get save data from GBA.emu")
            null
        }
    }

    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean {
        return try {
            val saveFile = getSaveFile(context, savePath) ?: return false
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(saveData)
            Timber.i("Wrote save data to: ${saveFile.absolutePath}")
            true
        } catch (_: Exception) {
            Timber.e("Failed to set save data for GBA.emu")
            false
        }
    }

    override fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(getPackageName(), 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getPackageName(): String = "nl.melikootje.gba.app"

    private fun getSaveFile(context: Context, savePath: String?): File? {
        return if (savePath != null) {
            File(savePath)
        } else {
            // GBA.emu saves in Android/data/nl.melikootje.gba.app/files
            val gbaEmuDir = File(context.getExternalFilesDir(null)?.parent, "nl.melikootje.gba.app/files")
            if (gbaEmuDir.exists()) {
                gbaEmuDir.listFiles()?.firstOrNull {
                    it.extension == "sav" || it.extension == "sa1"
                }
            } else {
                null
            }
        }
    }
}

/**
 * Adapter for Pizza Boy GBA (paid)
 */
open class PizzaBoyGBAAdapter : EmulatorAdapter() {
    override val supportsVideoConfig: Boolean = false
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", uri)
            }
            context.startActivity(intent)
            Timber.i("Launched Pizza Boy GBA with ROM: $romPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Pizza Boy GBA")
            false
        }
    }
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? = trySaveRead(context, savePath)
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean = trySaveWrite(context, savePath, saveData)
    override fun isInstalled(context: Context): Boolean = isPackageInstalled(context, getPackageName())
    override fun getPackageName(): String = "it.dbtecno.pizzaboygba"
}

/**
 * Adapter for Pizza Boy GBA Free
 */
class PizzaBoyGBAFreeAdapter : PizzaBoyGBAAdapter() {
    override fun getPackageName(): String = "it.dbtecno.pizzaboygbafree"
}

/**
 * Adapter for John GBA (paid)
 */
open class JohnGBAAdapter : EmulatorAdapter() {
    override val supportsVideoConfig: Boolean = false
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", uri)
            }
            context.startActivity(intent)
            Timber.i("Launched John GBA with ROM: $romPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch John GBA")
            false
        }
    }
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? = trySaveRead(context, savePath)
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean = trySaveWrite(context, savePath, saveData)
    override fun isInstalled(context: Context): Boolean = isPackageInstalled(context, getPackageName())
    override fun getPackageName(): String = "com.johnemulators.johngba"
}

/**
 * Adapter for John GBA Lite
 */
class JohnGBALiteAdapter : JohnGBAAdapter() {
    override fun getPackageName(): String = "com.johnemulators.johngbalite"
}

/**
 * Adapter for Lemuroid (multi-system)
 */
class LemuroidAdapter : EmulatorAdapter() {
    override val supportsVideoConfig: Boolean = false
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", uri)
            }
            context.startActivity(intent)
            Timber.i("Launched Lemuroid with ROM: $romPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Lemuroid")
            false
        }
    }
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? = trySaveRead(context, savePath)
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean = trySaveWrite(context, savePath, saveData)
    override fun isInstalled(context: Context): Boolean = isPackageInstalled(context, getPackageName())
    override fun getPackageName(): String = "com.swordfish.lemuroid"
}

/**
 * Adapter for My OldBoy! (GBC)
 */
open class MyOldBoyAdapter : EmulatorAdapter() {
    override val supportsVideoConfig: Boolean = false
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", uri)
            }
            context.startActivity(intent)
            Timber.i("Launched My OldBoy! with ROM: $romPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch My OldBoy!")
            false
        }
    }
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? = trySaveRead(context, savePath)
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean = trySaveWrite(context, savePath, saveData)
    override fun isInstalled(context: Context): Boolean = isPackageInstalled(context, getPackageName())
    override fun getPackageName(): String = "com.fastemulator.gbc"
}

/**
 * Adapter for My OldBoy! Free (GBC)
 */
class MyOldBoyFreeAdapter : MyOldBoyAdapter() {
    override fun getPackageName(): String = "com.fastemulator.gbcfree"
}

/**
 * Adapter for Pizza Boy GBC
 */
class PizzaBoyGBCAdapter : EmulatorAdapter() {
    override val supportsVideoConfig: Boolean = false
    override suspend fun loadRom(context: Context, romPath: String, videoConfig: VideoConfig?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(getPackageName())
                val uri = getRomUri(context, romPath)
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", uri)
            }
            context.startActivity(intent)
            Timber.i("Launched Pizza Boy GBC with ROM: $romPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Pizza Boy GBC")
            false
        }
    }
    override suspend fun getSaveData(context: Context, savePath: String?): ByteArray? = trySaveRead(context, savePath)
    override suspend fun setSaveData(context: Context, savePath: String?, saveData: ByteArray): Boolean = trySaveWrite(context, savePath, saveData)
    override fun isInstalled(context: Context): Boolean = isPackageInstalled(context, getPackageName())
    override fun getPackageName(): String = "it.dbtecno.pizzaboygbc"
}

// Helper functions for save handling and package checks
private fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: Exception) { false }

private fun trySaveRead(context: Context, savePath: String?): ByteArray? = try {
    val file = savePath?.let { File(it) }
    if (file != null && file.exists()) file.readBytes() else null
} catch (_: Exception) { null }

private fun trySaveWrite(context: Context, savePath: String?, saveData: ByteArray): Boolean = try {
    val file = savePath?.let { File(it) } ?: return false
    file.parentFile?.mkdirs()
    file.writeBytes(saveData)
    true
} catch (_: Exception) { false }
