# Device Compatibility Guide

## Supported Android Versions

- **Minimum:** Android 8.0 (API 26, Oreo)
- **Target:** Android 15 (API 35)
- **Recommended:** Android 12+ for best BLE performance

## Feature Matrix by Android Version

### Android 8.0 - 11 (API 26-30)
- ✅ Classic Bluetooth messaging (full support)
- ✅ BLE GATT connections
- ⚠️ BLE advertising (if hardware supports)
- ⚠️ BLE scanning (with location permission)
- ✅ Photo/video/audio transfers
- Permissions: `BLUETOOTH`, `BLUETOOTH_ADMIN`, optional `ACCESS_FINE_LOCATION`

### Android 12+ (API 31+)
- ✅ All features fully supported
- ✅ Improved BLE with new permission model
- ✅ No location permission required for BLE
- Permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`

## Hardware Requirements

### Essential (Required)
- Bluetooth Classic support
- Bluetooth Low Energy (BLE) support

### Optional (Enhanced Experience)
- BLE Multi-Advertisement support → Enables device discovery by others
- Camera → Photo/video capture in-app
- Microphone → Voice message recording

## Device Capability Fallbacks

### No BLE Advertising Support
**Affected:** Some older/budget devices
**App Behavior:**
- Device can still connect to others via Classic Bluetooth
- Can scan and discover BLE-advertising peers
- Works as "client-only" for BLE discovery
- Full messaging capability maintained

**User Impact:** Must be discovered via Bluetooth settings pairing, not in-app discovery

### Classic-Only Bluetooth
**Affected:** Rare (most devices post-2013 have BLE)
**App Behavior:**
- Falls back to Classic Bluetooth RFComm
- Full messaging, file transfer works
- Slower discovery process

### Limited BLE GATT Support
**Affected:** Android 8.0-8.1 edge cases
**App Behavior:**
- Classic Bluetooth used as primary transport
- BLE used opportunistically

## Known Device-Specific Behaviors

### Samsung Devices
- **Excellent compatibility** across all models
- Strong BLE advertising support on Galaxy S6+
- Tested: Galaxy S23 Ultra (full feature support)

### Huawei/Honor Devices
- Pre-Google Services ban: Full support
- Newer models: Classic Bluetooth reliable, BLE may vary

### Xiaomi/Redmi Devices
- Generally good support
- Some MIUI versions require manual permission review
- Battery optimization may need exemption for background service

### Google Pixel Devices
- **Excellent compatibility**
- Reference implementation for Android features

## Troubleshooting by Device Age

### Modern Devices (2020+)
- All features should work out of the box
- If issues occur, check app permissions in Settings

### Mid-Range Devices (2016-2019)
- Grant location permission for BLE scanning on pre-Android 12
- If discovery fails, use manual Bluetooth pairing
- Classic Bluetooth works reliably

### Older Devices (2013-2015)
- May need to pair via system Bluetooth settings first
- BLE advertising may not be available
- Classic Bluetooth fully functional
- Consider disabling battery optimization for app

## Performance Optimization by Device

### High-End Devices
- BLE connections preferred (lower latency)
- Concurrent connections supported
- Large file transfers (up to 15MB) reliable

### Budget/Older Devices
- Classic Bluetooth auto-selected when BLE unreliable
- Smaller file chunking for stability
- Connection retry logic handles flaky hardware

## Testing Recommendations

To ensure compatibility, we've tested across:
- ✅ Android 10 (Samsung device)
- ✅ Android 15 (Latest flagship)
- ✅ BLE <-> Classic mixed connections
- ✅ Null advertiser handling
- ✅ Permission-denied scenarios

## Future Compatibility Plans

- Maintain backward compatibility to Android 8.0
- Add Android 16+ features as they become available
- Continue testing on diverse hardware
- Monitor BLE stack improvements in AOSP

## For Developers

### Adding Version-Specific Features

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Android 12+ specific code
} else {
    // Fallback for older versions
}
```

### Checking Hardware Capabilities

```kotlin
// Check BLE advertising support
val isAdvertisingSupported = bluetoothAdapter?.isMultipleAdvertisementSupported == true

// Check if advertiser available
val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
if (advertiser == null) {
    // Fallback to Classic or server-only mode
}
```

### Transport Selection Logic

The app automatically selects the best transport:
1. **BLE peer detected** → Use BLE GATT (preferred)
2. **BLE fails** → Fallback to Classic Bluetooth
3. **Classic unavailable** → WiFi Direct/TCP-LAN (future)
4. **All direct fail** → Mesh SDK (if configured)

## Support Matrix

| Feature | Android 8-11 | Android 12+ | Notes |
|---------|--------------|-------------|-------|
| Text Messages | ✅ | ✅ | All transports |
| Photos | ✅ | ✅ | Up to 10MB |
| Videos | ✅ | ✅ | Up to 15MB |
| Audio Messages | ✅ | ✅ | Record & playback |
| BLE Discovery | ⚠️¹ | ✅ | ¹Needs location |
| BLE Advertising | ⚠️² | ✅ | ²If HW supports |
| Classic Bluetooth | ✅ | ✅ | Always works |
| Background Service | ✅ | ✅ | May need battery exemption |

---

**Last Updated:** February 2026
**App Version:** Check `version.txt` for current release
