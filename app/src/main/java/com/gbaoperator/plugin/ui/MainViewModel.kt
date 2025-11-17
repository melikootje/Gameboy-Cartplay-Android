package com.gbaoperator.plugin.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gbaoperator.plugin.data.CartridgeInfo
import com.gbaoperator.plugin.emulator.* 
import com.gbaoperator.plugin.integration.EmulatorIntegrationService
import com.gbaoperator.plugin.core.GBOperatorInterface
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

// @HiltViewModel
class MainViewModel constructor(
    private val context: Context,
    private val gbOperator: GBOperatorInterface
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val emulatorAdapters = listOf(
        GBAEmuAdapter(),  // Already installed on emulator!
        MGBAAdapter(),
        MyBoyAdapter(),
        RetroArchAdapter(),
        PizzaBoyGBAAdapter(),
        PizzaBoyGBAFreeAdapter(),
        JohnGBAAdapter(),
        JohnGBALiteAdapter(),
        LemuroidAdapter(),
        MyOldBoyAdapter(),
        MyOldBoyFreeAdapter(),
        PizzaBoyGBCAdapter()
    )

    // Removed BroadcastReceiver to avoid permission generation

    init {
        initializeAvailableEmulators()
        // Removed BroadcastReceiver registration to avoid permission issues
        refreshConnection()
    }


    private fun initializeAvailableEmulators() {
        val emulators = emulatorAdapters.map { adapter ->
            EmulatorInfo(
                name = adapter.javaClass.simpleName.replace("Adapter", ""),
                packageName = adapter.getPackageName(),
                isInstalled = adapter.isInstalled(context)
            )
        }

        _uiState.value = _uiState.value.copy(
            availableEmulators = emulators.filter { it.isInstalled }
        )
    }

    private fun expectedRomFile(cartridge: com.gbaoperator.plugin.data.CartridgeInfo): File {
        val romsDir = File(context.getExternalFilesDir("roms"), "dumped")
        val filename = "${cartridge.gameCode}_${cartridge.title}.gba"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(romsDir, filename)
    }

    private fun updateCachedRomState(cartridgeInfo: com.gbaoperator.plugin.data.CartridgeInfo?) {
        val has = try {
            if (cartridgeInfo == null) false else expectedRomFile(cartridgeInfo).exists()
        } catch (_: Exception) { false }
        _uiState.value = _uiState.value.copy(hasCachedRom = has)
    }

    fun refreshConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val isConnected = gbOperator.connect()
                val deviceType = if (isConnected) gbOperator.getDeviceType() else null
                val deviceInfo = if (isConnected) gbOperator.getDeviceInfo() else null
                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    isLoading = false,
                    deviceType = deviceType,
                    deviceInfo = deviceInfo,
                    error = if (!isConnected) "GB Operator device not found. Please connect it." else null
                )
                if (isConnected) {
                    detectCartridge()
                } else {
                    updateCachedRomState(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh connection")
                _uiState.value = _uiState.value.copy(isConnected = false, isLoading = false, error = "Failed to connect: ${e.message}")
                updateCachedRomState(null)
            }
        }
    }

    fun detectCartridge() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingMessage = "Detecting cartridge...")
            try {
                val cartridgeInfo = gbOperator.getCartridgeInfo()
                _uiState.value = _uiState.value.copy(
                    cartridgeInfo = cartridgeInfo,
                    isLoading = false,
                    loadingMessage = null,
                    error = if (cartridgeInfo == null) "No cartridge detected" else null,
                    successMessage = if (cartridgeInfo != null) "Detected: ${cartridgeInfo.title}" else null
                )
                updateCachedRomState(cartridgeInfo)
            } catch (e: Exception) {
                Timber.e(e, "Failed to detect cartridge")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Failed to detect cartridge: ${e.message}"
                )
                updateCachedRomState(null)
            }
        }
    }

    fun dumpRom() {
        val intent = Intent(context, EmulatorIntegrationService::class.java).apply {
            action = EmulatorIntegrationService.ACTION_LOAD_ROM
        }
        context.startService(intent)
    }

    fun launchEmulatorPreferred(packageName: String) {
        val cart = _uiState.value.cartridgeInfo
        val hasCached = _uiState.value.hasCachedRom
        val intent = Intent(context, EmulatorIntegrationService::class.java).apply {
            action = EmulatorIntegrationService.ACTION_LOAD_ROM
            putExtra(EmulatorIntegrationService.EXTRA_EMULATOR_PACKAGE, packageName)
            if (hasCached && cart != null) {
                val path = expectedRomFile(cart).absolutePath
                putExtra(EmulatorIntegrationService.EXTRA_ROM_PATH, path)
            }
        }
        context.startService(intent)
    }

    fun backupSave() {
        val intent = Intent(context, EmulatorIntegrationService::class.java).apply {
            action = EmulatorIntegrationService.ACTION_SYNC_SAVE
        }
        context.startService(intent)
    }

    fun restoreSave() {
        val intent = Intent(context, EmulatorIntegrationService::class.java).apply {
            action = EmulatorIntegrationService.ACTION_LOAD_SAVE
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        // Removed receiver unregistration since we're not using it
        viewModelScope.launch {
            gbOperator.disconnect()
        }
    }
}

data class MainUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val cartridgeInfo: CartridgeInfo? = null,
    val availableEmulators: List<EmulatorInfo> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val loadingMessage: String? = null,
    val loadingProgress: Float = 0f,
    val deviceType: GBOperatorInterface.DeviceType? = null,
    val deviceInfo: String? = null,
    val videoConfig: VideoConfig = VideoConfig(),
    val hasCachedRom: Boolean = false
)

data class EmulatorInfo(
    val name: String,
    val packageName: String,
    val isInstalled: Boolean
)
