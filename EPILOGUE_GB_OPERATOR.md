# Epilogue GB Operator Support

## Overview

Gameboy Cartplay includes support for the **Epilogue GB Operator**, a modern USB cartridge reader for Game Boy, Game Boy Color, and Game Boy Advance cartridges.

## Device Specifications

- **Vendor ID**: 0x16D0
- **Product ID**: 0x0F3B
- **Max Transfer Speed**: 400 KB/s
- **Protocol**: USB CDC/Serial interface
- **Display Name**: "Epilogue GB Operator"

## Features

The Epilogue GB Operator is detected automatically and provides:

- ✅ ROM dumping from GB/GBC/GBA cartridges
- ✅ Save data backup and restore
- ✅ Cartridge detection and info reading
- ✅ Enhanced performance (400 KB/s transfer speed)
- ✅ Native USB OTG support for Android devices

## Detection

The app automatically detects Epilogue GB Operator devices when:

1. The device is connected via USB OTG
2. The app has USB permission granted
3. The device responds with "GB Operator" to the RESET command

## Technical Details

### USB Communication

The Epilogue GB Operator uses a USB CDC serial interface:

- **Interface Class**: USB_CLASS_CDC_DATA or USB_CLASS_VENDOR_SPEC
- **Endpoint Type**: Bulk transfer
- **Baudrate**: Configured via USB control transfer
- **Command Protocol**: Text-based commands with newline terminators

### Device Detection Flow

```kotlin
// VID/PID check
if (device.vendorId == 0x16D0 && device.productId == 0x0F3B) {
    // Send RESET command
    sendCommand("RESET\n")
    
    // Expect response containing "GB Operator"
    if (response.contains("GB Operator")) {
        deviceType = EPILOGUE_GB_OPERATOR
    }
}
```

### Performance Characteristics

- **ROM Dump Speed**: Up to 400 KB/s
- **Save Backup Speed**: Fast (small save sizes complete in <1 second)
- **Cartridge Detection**: Instant
- **USB Latency**: Low (optimized bulk transfers)

## Compatibility

### Tested Configurations

- ✅ Android 10+ with USB OTG
- ✅ Pixel devices (verified)
- ✅ Samsung Galaxy devices (verified)
- ✅ Generic USB OTG adapters

### Cartridge Compatibility

The Epilogue GB Operator supports all standard cartridge types:

- **Game Boy**: DMG cartridges
- **Game Boy Color**: CGB cartridges
- **Game Boy Advance**: AGB cartridges

## Troubleshooting

### Device Not Detected

1. **Check USB OTG Support**
   - Verify your Android device supports USB OTG
   - Try the GB Operator with another device to confirm it's working

2. **Check USB Permission**
   - Go to Android Settings → Apps → Gameboy Cartplay → Permissions
   - Ensure storage and USB permissions are granted

3. **Check Device Logs**
   ```bash
   adb logcat -s GBOperatorInterface:* UsbDeviceManager:*
   ```

### Slow Transfer Speeds

- Ensure you're using a quality USB OTG adapter
- Try a different USB cable
- Close background apps that may be using USB resources

### Connection Drops

- Some Android devices have aggressive power management
- Go to Settings → Battery → Unrestricted battery usage
- Add Gameboy Cartplay to the unrestricted list

## Comparison with Other Readers

| Feature | Epilogue GB Operator | GBxCart RW | Joey Jr | GBFlash |
|---------|---------------------|------------|---------|---------|
| Max Speed | 400 KB/s | 200 KB/s | 150 KB/s | 550 KB/s |
| USB Type | USB-C | Micro USB | Micro USB | USB-C |
| Android Support | ✅ Native | ✅ Via FlashGBX | ✅ Via FlashGBX | ✅ Via FlashGBX |
| Modern Design | ✅ Yes | Older | Older | ✅ Yes |
| Active Development | ✅ Yes | Yes | Limited | Yes |

## Additional Resources

- [Epilogue Official Website](https://www.epilogue.co/)
- [GB Operator Documentation](https://www.epilogue.co/support)
- [FlashGBX Protocol Documentation](https://github.com/lesserkuma/FlashGBX)

## Support

For Epilogue GB Operator specific issues:
- Contact Epilogue support: support@epilogue.co
- Visit: https://www.epilogue.co/support

For app integration issues:
- Open an issue on the Gameboy Cartplay GitHub repository
- Include logcat output with USB debug logs

---

**Last Updated**: November 17, 2025  
**Supported Since**: v0.1.0
