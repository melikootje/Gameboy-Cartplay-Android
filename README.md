# Gameboy Cartplay

A modern Android app for managing Game Boy and Game Boy Advance cartridges with direct emulator integration. This project is partially vibecoded by the means of AI.

**<img width="320" height="480" alt="image" src="https://github.com/user-attachments/assets/b0b7263c-1c9d-4208-9f57-cd9b9b6a27da" />
**
<img width="320" height="480" alt="image" src="https://github.com/user-attachments/assets/b1a2b3ae-5aba-41d7-9f86-f9870bd14dc3" />



## Overview


Gameboy Cartplay is a native Android application that bridges physical GB/GBC/GBA cartridge readers with popular Android emulators. Dump ROMs, backup saves, and launch games directly in your favorite emulator—all from one app.

### Key Features

- **Cartridge Reader Support**: Works with GBxCart RW, GBFlash, Joey Jr, and other FlashGBX-compatible USB devices
- **ROM Dumping**: Extract ROM files from physical cartridges
- **Save Management**: Backup and restore save data between cartridges and emulators
- **Direct Emulator Launch**: One-tap launching into installed emulators
- **Multi-Emulator Support**: Integrates with 12+ popular GBA/GBC emulators
- **Deep Link Integration**: Launch ROMs via command-line tools or scripts
- **Material Design 3**: Clean, modern UI with Jetpack Compose

## Supported Hardware

### Cartridge Readers

The following USB cartridge readers are compatible:

- **Epilogue GB Operator** (VID: 0x16D0, PID: 0x0F3B) - Enhanced performance and features
- **GBxCart RW** (InsideGadgets family)
- **GBFlash** (compatible variants)
- **Joey Jr**
- **FlashGBX-compatible readers** (USB HID/CDC based)

**Note**: On first use, Android will prompt for USB permission. Grant access to communicate with the device.

## Supported Emulators

### Game Boy Advance (GBA)

- **mGBA** (`com.endrift.mgba`) - Supports video config extras
- **My Boy!** (`com.fastemulator.gba`)
- **My Boy! Free** (`com.fastemulator.gbafree`)
- **Pizza Boy GBA** (`it.dbtecno.pizzaboygba`)
- **Pizza Boy GBA Free** (`it.dbtecno.pizzaboygbafree`)
- **John GBA** (`com.johnemulators.johngba`)
- **John GBA Lite** (`com.johnemulators.johngbalite`)
- **RetroArch** (`com.retroarch`) - Uses specialized mGBA core launch intent with file fallback
- **Lemuroid** (`com.swordfish.lemuroid`)

### Game Boy / Game Boy Color (GBC)

- **My OldBoy!** (`com.fastemulator.gbc`)
- **My OldBoy! Free** (`com.fastemulator.gbcfree`)
- **Pizza Boy GBC** (`it.dbtecno.pizzaboygbc`)

### Notes

- Emulators are launched via Android `ACTION_VIEW` intents with FileProvider URIs for secure file access
- RetroArch uses a specialized launch intent (`com.retroarch.LAUNCH`) with mGBA core when available
- Some emulators may prompt for file access permissions on first launch

## Installation

### Prerequisites

- Android 8.0 (API 26) or higher
- USB OTG support (for cartridge reader connection)
- At least one supported emulator installed

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/melikootje/Gameboy-Cartplay-Android.git 
   cd Gameboy_Cartplay_Android
   ```

2. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Direct Installation

Pre-built APKs available under the release tab of this repo but be aware that its currently impossible to manually install the app on a device by package installer directly from android due to a lot of bugs and errors while trying to install it that way. The only working method to install this app is through adb install at the very moment.

## Usage

### Basic Workflow

1. **Connect Hardware**
   - Plug your USB cartridge reader into your Android device via OTG adapter
   - Grant USB permission when prompted
   - Navigate to the "Device" tab and tap "Refresh Connection"

2. **Detect Cartridge**
   - Insert a GB/GBC/GBA cartridge into the reader
   - Tap "Detect" in the Device tab
   - View cartridge info (title, game code, ROM/save sizes)

3. **Launch Game**
   - Navigate to the "Launch" tab
   - Tap on an installed emulator
   - If ROM is cached: launches immediately
   - If not cached: dumps ROM then launches

4. **Manage Saves**
   - Navigate to the "Manage" tab
   - Use "Backup Save" to extract save data from cartridge
   - Use "Restore Save" to write save data back to cartridge
   - Use "Dump ROM" to extract ROM without launching

### Deep Link Integration

The app supports `gbaoperator://launch` deep links for scriptable workflows:

**Format:**
```
gbaoperator://launch?rom=<path>&pkg=<emulator-package>
```

**Parameters:**
- `rom` (required): Absolute path to ROM file (e.g., `/sdcard/Download/game.gba`)
- `pkg` (optional): Target emulator package name. If omitted, uses best available emulator.

**Example:**
```bash
adb shell am start -a android.intent.action.VIEW \
  -d "gbaoperator://launch?rom=/sdcard/Download/pokemon.gba&pkg=com.endrift.mgba"
```

## Developer Tools

### push_rom.sh Script

A companion shell script for pushing ROMs to devices and triggering app launches:

**Basic Usage:**
```bash
./push_rom.sh /path/to/rom.gba
```

**Options:**
- `-s <serial>` - Target specific device/emulator serial
- `-d <dest_dir>` - Destination directory (default: `/sdcard/Download`)
- `-n <dest_name>` - Custom filename
- `-P <package>` - Launch in specific emulator via ACTION_VIEW
- `-R` - Launch in RetroArch with specialized intent
- `-C <core>` - Override RetroArch core path
- `-F <config>` - Override RetroArch config path
- `-L` - Open app via deep link after push
- `-h` - Show help

**Examples:**

Push ROM and open in app:
```bash
./push_rom.sh -L /path/to/game.gba
```

Push and launch directly in mGBA:
```bash
./push_rom.sh -P com.endrift.mgba /path/to/game.gba
```

Push to specific device with custom destination:
```bash
./push_rom.sh -s R5CT42ABCD -d /sdcard/ROMs/GBA -n emerald.gba /path/to/game.gba
```

Launch in RetroArch with mGBA core:
```bash
./push_rom.sh -R /path/to/game.gba
```

**Script Requirements:**
- macOS/Linux with Bash 3.2+
- Android platform-tools (adb) in PATH
- Device connected via USB or network ADB

## Architecture

### Key Components

- **GBOperatorInterface**: USB communication layer for cartridge reader protocols
- **EmulatorAdapter**: Abstract base for emulator-specific launch logic
- **EmulatorIntegrationService**: Background service for ROM/save operations
- **MainActivity**: Compose UI with Device/Launch/Manage tabs
- **MainViewModel**: State management and business logic

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3
- **Concurrency**: Kotlin Coroutines + Flow
- **USB**: Android USB Host API
- **DI**: Manual dependency injection (no framework)
- **Build**: Gradle with Android Gradle Plugin 8.x

### File Paths

- **Dumped ROMs**: `/data/data/<package>/files/roms/dumped/`
- **Save Backups**: `/data/data/<package>/files/saves/backups/`
- **Temp ROMs**: `/data/data/<package>/cache/`
- **External Push Location**: `/sdcard/Download/` (configurable)

### FileProvider Configuration

The app uses FileProvider for secure file sharing with emulators:

- **Authority**: `<package>.fileprovider`
- **Paths**:
  - `external-path`: External storage access
  - `cache-path`: Temporary ROM files
  - `files-path`: Persistent ROM/save storage

## Project Structure

```
Gameboy_Cartplay_Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/gbaoperator/plugin/
│   │   │   ├── core/                # USB/hardware interfaces
│   │   │   │   └── GBOperatorInterface.kt
│   │   │   ├── data/                # Data models
│   │   │   │   └── CartridgeInfo.kt
│   │   │   ├── emulator/            # Emulator adapters
│   │   │   │   └── EmulatorAdapter.kt
│   │   │   ├── integration/         # Background services
│   │   │   │   └── EmulatorIntegrationService.kt
│   │   │   ├── ui/                  # Compose UI
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MainViewModel.kt
│   │   │   │   └── components/
│   │   │   └── usb/                 # USB device management
│   │   │       └── UsbDeviceManager.kt
│   │   ├── res/                     # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
├── push_rom.sh                      # Developer utility script
├── build.gradle
├── settings.gradle
└── README.md
```

## Troubleshooting

### Cartridge Reader Not Detected

- Ensure USB OTG adapter is working (test with other USB devices)
- Check that USB permission was granted in Android settings
- Try unplugging and reconnecting the reader
- Some devices may require enabling "OTG" in developer options

### Emulator Won't Launch

- Verify emulator is installed (`adb shell pm list packages | grep <package>`)
- Check that ROM file exists and is readable
- Review logcat for errors: `adb logcat -s GBOperatorPlugin:*`
- Try launching emulator manually to verify it's working

### ROM Files from push_rom.sh Not Launching

- Ensure you used `-L` flag or opened app manually after push
- Check file was pushed: `adb shell ls -l /sdcard/Download/<filename>`
- Verify app has storage permissions in Android settings
- Use deep link manually: `adb shell am start -a android.intent.action.VIEW -d "gbaoperator://launch?rom=/sdcard/Download/<filename>"`

### Save Backup/Restore Fails

- Confirm cartridge has battery-backed save (not all do)
- Check cartridge battery isn't dead (very old carts may need replacement)
- Verify reader supports save operations (most FlashGBX-compatible do)
- Try a known-working cartridge to isolate hardware vs. software issues

### RetroArch Specialized Launch Fails

- Install mGBA core in RetroArch: Settings → Core Downloader → Game Boy Advance (mGBA)
- Verify core path: `/data/data/com.retroarch/cores/mgba_libretro_android.so`
- Check config path: `/storage/emulated/0/RetroArch/retroarch.cfg`
- Override paths with `-C` and `-F` flags if your RetroArch uses custom locations

## Contributing

Contributions are welcome! Areas for improvement:

- Additional cartridge reader support (new USB VID/PID pairs)
- More emulator adapters
- Video configuration UI
- Game library management
- Multiplayer/link cable emulation
- Cheat code support

## License

[Insert your license here]

## Acknowledgments

- FlashGBX project for cartridge reader protocols
- Emulator developers for supporting file intents
- InsideGadgets for GBxCart RW hardware
- Android USB community for documentation and examples

## Support

For issues, feature requests, or questions:
- Open an issue on GitHub
- Check existing documentation in this README
- Review logcat output for debugging

---

**Version**: 0.1.0  
**Minimum Android**: 8.0 (API 26)  
**Target Android**: 14 (API 34)  
**Last Updated**: 2025-11-17

