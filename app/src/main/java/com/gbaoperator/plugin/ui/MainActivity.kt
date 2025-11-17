package com.gbaoperator.plugin.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbaoperator.plugin.data.CartridgeInfo
import com.gbaoperator.plugin.integration.EmulatorIntegrationService
import com.gbaoperator.plugin.ui.components.VideoConfigSelector
import com.gbaoperator.plugin.ui.theme.GBAOperatorTheme

sealed class Screen(val route: String, val label: String, val icon: ImageVector)
object DeviceScreen : Screen("device", "Device", Icons.Default.Devices)
object LaunchScreen : Screen("launch", "Launch", Icons.Default.PlayArrow)
object ManageScreen : Screen("manage", "Manage", Icons.Default.Folder)

val navigationItems = listOf(DeviceScreen, LaunchScreen, ManageScreen)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent {
                GBAOperatorTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
            // Handle deep link if present
            handleDeepLink(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize Compose UI", e)
            finish()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent) {
        val data: Uri? = intent.data
        if (data != null && data.scheme == "gbaoperator" && data.host == "launch") {
            val rom = data.getQueryParameter("rom")
            val pkg = data.getQueryParameter("pkg")
            if (!rom.isNullOrBlank()) {
                val svc = android.content.Intent(this, EmulatorIntegrationService::class.java).apply {
                    action = EmulatorIntegrationService.ACTION_LOAD_ROM
                    putExtra(EmulatorIntegrationService.EXTRA_ROM_PATH, rom)
                    if (!pkg.isNullOrBlank()) {
                        putExtra(EmulatorIntegrationService.EXTRA_EMULATOR_PACKAGE, pkg)
                    }
                }
                startService(svc)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // Use real GBOperatorInterface for actual hardware and emulator launching
    val context = LocalContext.current
    val gbOperator = remember { com.gbaoperator.plugin.core.GBOperatorInterface(context) }
    val viewModel = remember { MainViewModel(context, gbOperator) }
    val uiState by viewModel.uiState.collectAsState()
    var selectedScreen by remember { mutableStateOf<Screen>(DeviceScreen) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(uiState.hasCachedRom) {
        if (uiState.hasCachedRom) {
            snackbarHostState.showSnackbar("ROM available for quick launch")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                navigationItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = selectedScreen.route == screen.route,
                        onClick = { selectedScreen = screen }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = uiState.loadingProgress)
                uiState.loadingMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            when (selectedScreen) {
                DeviceScreen -> DevicePage(uiState, viewModel)
                LaunchScreen -> LaunchPage(uiState, viewModel)
                ManageScreen -> ManagePage(uiState, viewModel)
            }
        }
    }
}

@Composable
fun DevicePage(uiState: MainUiState, viewModel: MainViewModel) {
    DeviceConnectionCard(uiState, viewModel::refreshConnection)
    if (uiState.isConnected) {
        DeviceStatusCard(uiState.cartridgeInfo, uiState.isLoading, viewModel::detectCartridge)
    }
}

@Composable
fun LaunchPage(uiState: MainUiState, viewModel: MainViewModel) {
    if (uiState.cartridgeInfo != null) {
        VideoConfigSelector(
            videoConfig = uiState.videoConfig,
            onConfigChange = { /* viewModel.updateVideoConfig(it) */ }
        )
        EmulatorList(uiState, viewModel::launchEmulatorPreferred)
    } else {
        Text("Please insert and detect a cartridge in the 'Device' tab before launching an emulator.")
    }
}

@Composable
fun ManagePage(uiState: MainUiState, viewModel: MainViewModel) {
    if (uiState.cartridgeInfo != null) {
        OperationsCard(uiState.isLoading, viewModel::dumpRom, viewModel::backupSave, viewModel::restoreSave)
    } else {
        Text("Please insert and detect a cartridge in the 'Device' tab to manage it.")
    }
}

@Composable
fun DeviceConnectionCard(uiState: MainUiState, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Cable, "USB", Modifier.size(32.dp), if (uiState.isConnected) Color(0xFF4CAF50) else Color.Gray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiState.deviceInfo ?: "GB Operator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (uiState.isConnected) "Connected" else "Disconnected", style = MaterialTheme.typography.bodyMedium, color = if (uiState.isConnected) Color(0xFF4CAF50) else Color.Red)
            }
            IconButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                Icon(Icons.Default.Refresh, "Refresh Connection")
            }
        }
    }
}

@Composable
fun DeviceStatusCard(cartridgeInfo: CartridgeInfo?, isLoading: Boolean, onDetectCartridge: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Cartridge Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onDetectCartridge, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Detect")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (cartridgeInfo != null) CartridgeInfoDisplay(cartridgeInfo) else Text("No cartridge detected. Insert a cartridge and press Detect.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun CartridgeInfoDisplay(cartridge: CartridgeInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow("Title", cartridge.title)
        InfoRow("Game Code", cartridge.gameCode)
        InfoRow("Maker Code", cartridge.makerCode)
        InfoRow("Version", cartridge.version.toString())
        InfoRow("ROM Size", "${cartridge.romSize / 1024 / 1024}MB")
        cartridge.saveSize?.let { InfoRow("Save Size", "${it / 1024}KB") }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text("$label:", fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}

@Composable
fun EmulatorList(uiState: MainUiState, onLaunch: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Available Emulators", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (uiState.availableEmulators.isEmpty()) {
                Text("No compatible emulators found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 200.dp)) {
                    items(uiState.availableEmulators) { emulator ->
                        EmulatorCard(
                            emulator = emulator,
                            isEnabled = uiState.cartridgeInfo != null,
                            hasCachedRom = uiState.hasCachedRom
                        ) { onLaunch(emulator.packageName) }
                    }
                }
            }
        }
    }
}

@Composable
fun EmulatorCard(emulator: EmulatorInfo, isEnabled: Boolean, hasCachedRom: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(isEnabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (emulator.isInstalled) Icons.Default.CheckCircle else Icons.Outlined.Close, if (emulator.isInstalled) "Installed" else "Not Installed", tint = if (emulator.isInstalled) Color(0xFF4CAF50) else Color.Gray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(emulator.name, fontWeight = FontWeight.Medium)
                Text(emulator.packageName, style = MaterialTheme.typography.bodySmall)
                val actionText = if (hasCachedRom) "Tap to Launch ROM" else "Tap to Dump & Launch"
                Text(actionText, style = MaterialTheme.typography.bodySmall, color = Color(0xFF607D8B))
            }
        }
    }
}

@Composable
fun OperationsCard(isLoading: Boolean, onDumpRom: () -> Unit, onBackupSave: () -> Unit, onRestoreSave: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Cartridge Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDumpRom, enabled = !isLoading, modifier = Modifier.weight(1f)) { Text("Dump ROM") }
                Button(onClick = onBackupSave, enabled = !isLoading, modifier = Modifier.weight(1f)) { Text("Backup Save") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRestoreSave, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Restore Save") }
        }
    }
}
