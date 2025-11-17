# Changelog

## v0.1.0 - 2025-11-17

### Initial Release

**Features:**
- USB cartridge reader support (Epilogue GB Operator, GBxCart RW, GBFlash, Joey Jr, FlashGBX-compatible)
- ROM dumping from Game Boy/Game Boy Color/Game Boy Advance cartridges
- Save data backup and restore
- Direct emulator launching with 12+ supported emulators
- Material Design 3 UI with Jetpack Compose
- Deep link integration (`gbaoperator://launch`)
- Companion `push_rom.sh` script for development workflows

**Supported Emulators:**
- GBA: mGBA, My Boy!, Pizza Boy GBA, John GBA, RetroArch, Lemuroid
- GBC: My OldBoy!, Pizza Boy GBC

**Technical:**
- Kotlin with Coroutines
- Jetpack Compose UI
- Android USB Host API
- FileProvider for secure emulator file sharing
- Specialized RetroArch launch intent support

