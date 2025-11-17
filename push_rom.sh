#!/bin/bash

# Script to push a GBA ROM to an Android emulator or a physical device for testing
# Usage:
#   ./push_rom.sh [options] /path/to/your/rom.gba
#
# Options:
#   -s <serial>     ADB device/emulator serial (from `adb devices`). If omitted, auto-detect.
#   -d <dest_dir>   Destination directory on device (default: /sdcard/Download)
#   -n <dest_name>  Destination filename (default: basename of ROM)
#   -P <package>    After push, try to launch ROM in this emulator package (generic ACTION_VIEW)
#   -R              After push, try specialized RetroArch launch (com.retroarch.LAUNCH)
#   -C <core>       RetroArch core path override (default: /data/data/com.retroarch/cores/mgba_libretro_android.so)
#   -F <config>     RetroArch config path override (default: /storage/emulated/0/RetroArch/retroarch.cfg)
#   -L              After push, open gbaoperator://launch?rom=... deep-link to trigger app launch
#   -h              Show help
#
# Examples:
#   ./push_rom.sh ~/Downloads/pokemon_emerald.gba
#   ./push_rom.sh -s emulator-5554 -P com.endrift.mgba ~/Downloads/pokemon_emerald.gba
#   ./push_rom.sh -R -C /data/data/com.retroarch/cores/mgba_libretro_android.so \
#                 -F /storage/emulated/0/RetroArch/retroarch.cfg \
#                 -d /sdcard/ROMs/GBA -n emerald.gba ~/Downloads/pokemon_emerald.gba

set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [options] <path-to-rom.gba>

Options:
  -s <serial>     ADB device/emulator serial (from \
                  \`adb devices\`). If omitted, auto-detect.
  -d <dest_dir>   Destination directory on device (default: /sdcard/Download)
  -n <dest_name>  Destination filename (default: basename of ROM)
  -P <package>    After push, try to launch ROM in this emulator package (generic ACTION_VIEW)
  -R              After push, try specialized RetroArch launch (com.retroarch.LAUNCH)
  -C <core>       RetroArch core path (default: /data/data/com.retroarch/cores/mgba_libretro_android.so)
  -F <config>     RetroArch config path (default: /storage/emulated/0/RetroArch/retroarch.cfg)
  -L              After push, open gbaoperator://launch?rom=... deep-link to trigger app launch
  -h              Show this help

Examples:
  $0 ~/Downloads/pokemon_emerald.gba
  $0 -s emulator-5554 -P com.endrift.mgba ~/Downloads/pokemon_emerald.gba
  $0 -R -C /data/data/com.retroarch/cores/mgba_libretro_android.so \\
     -F /storage/emulated/0/RetroArch/retroarch.cfg \\
     -d /sdcard/ROMs/GBA -n emerald.gba ~/Downloads/pokemon_emerald.gba
EOF
}

# Ensure adb exists
if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH. Please install Android platform-tools and ensure 'adb' is available."
  exit 1
fi

ADB_SERIAL=""
DEST_DIR="/sdcard/Download"
DEST_NAME=""
LAUNCH_PKG=""
RETROARCH=false
RETROARCH_CORE="/data/data/com.retroarch/cores/mgba_libretro_android.so"
RETROARCH_CFG="/storage/emulated/0/RetroArch/retroarch.cfg"
OPEN_DEEPLINK=false

# Parse options (short options only for macOS bash portability)
while getopts ":s:d:n:P:RC:F:Lh" opt; do
  case $opt in
    s) ADB_SERIAL="$OPTARG" ;;
    d) DEST_DIR="$OPTARG" ;;
    n) DEST_NAME="$OPTARG" ;;
    P) LAUNCH_PKG="$OPTARG" ;;
    R) RETROARCH=true ;;
    C) RETROARCH_CORE="$OPTARG" ;;
    F) RETROARCH_CFG="$OPTARG" ;;
    L) OPEN_DEEPLINK=true ;;
    h) usage; exit 0 ;;
    \?) echo "Invalid option: -$OPTARG"; usage; exit 1 ;;
    :) echo "Option -$OPTARG requires an argument."; usage; exit 1 ;;
  esac
done
shift $((OPTIND-1))

# ROM path argument
if [ $# -eq 0 ]; then
  usage
  echo "\nThis will push the ROM to ${DEST_DIR%/}/<name>.gba on the selected device,"
  echo "where supported emulators can read it."
  exit 1
fi

ROM_PATH="$1"

if [ ! -f "$ROM_PATH" ]; then
  echo "Error: ROM file not found: $ROM_PATH"
  exit 1
fi

# Set default destination name to the ROM's basename if not provided
if [ -z "$DEST_NAME" ]; then
  DEST_NAME="$(basename "$ROM_PATH")"
fi

# Helper: run adb with optional -s <serial>
adb_cmd() {
  if [ -n "$ADB_SERIAL" ]; then
    adb -s "$ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}

# Select device if serial not provided
select_device_if_needed() {
  if [ -n "$ADB_SERIAL" ]; then
    return
  fi
  DEVICES=()
  while read -r serial state; do
    if [ "$state" = "device" ] && [ -n "$serial" ]; then
      DEVICES+=("$serial")
    fi
  done < <(adb devices | awk 'NR>1 {print $1, $2}')

  if [ ${#DEVICES[@]} -eq 0 ]; then
    echo "Error: No connected ADB devices or emulators found."
    echo "Tip: start an emulator (e.g., Pixel API 34) or plug in a device with USB debugging enabled."
    exit 1
  elif [ ${#DEVICES[@]} -eq 1 ]; then
    ADB_SERIAL="${DEVICES[0]}"
  else
    echo "Multiple devices/emulators detected. Select a target:"
    select choice in "${DEVICES[@]}"; do
      if [ -n "$choice" ]; then
        ADB_SERIAL="$choice"
        break
      fi
    done
  fi
}

# Do the push
select_device_if_needed
DEVICE_MODEL="$(adb_cmd shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
DEVICE_ABI="$(adb_cmd shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' || true)"
FULL_DEST_PATH="${DEST_DIR%/}/$DEST_NAME"

echo "Pushing ROM to device..."
[ -n "$ADB_SERIAL" ] && echo "Target serial: $ADB_SERIAL"
[ -n "$DEVICE_MODEL" ] && echo "Device: $DEVICE_MODEL ($DEVICE_ABI)"
echo "Source: $ROM_PATH"
echo "Destination: $FULL_DEST_PATH"
echo ""

adb_cmd shell mkdir -p "${DEST_DIR%/}"
adb_cmd push "$ROM_PATH" "$FULL_DEST_PATH"

if [ $? -ne 0 ]; then
  echo "\n✗ Failed to push ROM to device"
  exit 1
fi

echo "\n✓ ROM successfully pushed!"
adb_cmd shell ls -lh "$FULL_DEST_PATH" || true

# Optional: auto-launch via deep link
if [ "${OPEN_DEEPLINK:-false}" = true ]; then
  echo "\nOpening deep link to app..."
  DL="gbaoperator://launch?rom=$(printf %s "$FULL_DEST_PATH" | sed 's/:/%3A/g; s/\//%2F/g; s/\?/%3F/g; s/=/%3D/g; s/&/%26/g')"
  if [ -n "$LAUNCH_PKG" ]; then
    DL="$DL&pkg=$(printf %s "$LAUNCH_PKG" | sed 's/:/%3A/g; s/\//%2F/g; s/\?/%3F/g; s/=/%3D/g; s/&/%26/g')"
  fi
  adb_cmd shell am start -a android.intent.action.VIEW -d "$DL" >/dev/null 2>&1 || true
  echo "✓ Deep link sent to app"
fi

# Existing auto-launch flows
if $RETROARCH; then
  echo "\nAttempting specialized RetroArch launch..."
  # Try specialized RA intent first
  if adb_cmd shell am start \
      -a com.retroarch.LAUNCH \
      -p com.retroarch \
      --es ROM "$FULL_DEST_PATH" \
      --es LIBRETRO "$RETROARCH_CORE" \
      --es CONFIGFILE "$RETROARCH_CFG" >/dev/null 2>&1; then
    echo "✓ RetroArch launch intent sent"
    exit 0
  else
    echo "RetroArch specialized launch failed. Falling back to generic open..."
    # Fallback: generic ACTION_VIEW with package
    adb_cmd shell am start \
      -a android.intent.action.VIEW \
      -p com.retroarch \
      -d "file://$FULL_DEST_PATH" \
      -t application/octet-stream >/dev/null 2>&1 || true
    echo "(If RetroArch didn’t open, ensure the mgba core is installed and config path is valid)"
    exit 0
  fi
fi

if [ -n "$LAUNCH_PKG" ]; then
  echo "\nAttempting to launch in $LAUNCH_PKG..."
  # Generic ACTION_VIEW launch using file:// (works for most emulators reading /sdcard)
  if adb_cmd shell am start \
      -a android.intent.action.VIEW \
      -p "$LAUNCH_PKG" \
      -d "file://$FULL_DEST_PATH" \
      -t application/octet-stream >/dev/null 2>&1; then
    echo "✓ Launch intent sent to $LAUNCH_PKG"
  else
    echo "✗ Failed to launch $LAUNCH_PKG with ACTION_VIEW. You can open the ROM manually inside the emulator."
  fi
fi

exit 0
