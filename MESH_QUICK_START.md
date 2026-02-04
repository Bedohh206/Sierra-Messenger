# Quick Start - Mesh Messaging

## TL;DR - 3 Steps to Run

### Android
```bash
# 1. Build
cd android
./gradlew :app:assembleDebug

# 2. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Grant permissions and test
# Open app → Allow Bluetooth/Location → See nearby devices
```

### iOS
```bash
# 1. Open in Xcode
open ios/BluetoothMessenger.xcodeproj

# 2. Build & Run (⌘R)

# 3. Grant Bluetooth permission and test
```

## Testing Without Mesh SDK

Both platforms work without Bridgefy:
- ✅ BLE device discovery
- ✅ Text messaging (small)
- ✅ Store-and-forward with ACKs
- ❌ Multi-hop routing (requires Bridgefy)

## Adding Bridgefy (Optional)

See [BRIDGEFY_INTEGRATION.md](BRIDGEFY_INTEGRATION.md) for detailed steps.

**Quick version:**

### Android
```properties
# gradle.properties
BRIDGEFY_API_KEY=your-key-here
```

```kotlin
// app/build.gradle.kts
implementation("me.bridgefy:android-sdk:2.0.0")
```

### iOS
```ruby
# Podfile
pod 'BridgefySDK', '~> 2.0'
```

```xml
<!-- Info.plist -->
<key>BridgefyAPIKey</key>
<string>your-key-here</string>
```

Then rebuild and mesh routing will activate automatically.

## What Works Now

| Feature | Android | iOS | Notes |
|---------|---------|-----|-------|
| BLE Discovery | ✅ | 🚧 | iOS needs CoreBluetooth wiring |
| Text Messages | ✅ | ✅ | Direct peer-to-peer |
| Voice Messages | ✅ | 🚧 | iOS needs AVFoundation |
| Image Sharing | ✅ | 🚧 | iOS needs chunked transfer |
| Store-and-Forward | ✅ | ✅ | Outbox with retry |
| ACK Protocol | ✅ | ✅ | 15s timeout, 10s retry |
| Mesh Routing | 🔌 | 🔌 | Requires Bridgefy SDK |
| Wi-Fi Direct | 🚧 | N/A | Android stub, iOS not applicable |

Legend: ✅ Working | 🚧 Stub/TODO | 🔌 Optional (needs config) | N/A Not applicable

## Next Priority: iOS BLE

To enable iOS device discovery and messaging:

1. Add CoreBluetooth peripheral/central managers
2. Implement same GATT service UUID (`00001234-0000-1000-8000-00805f9b34fb`)
3. Wire to MessageRouter for send/receive
4. Update UI to show discovered peers

See `app/src/main/java/com/btmessenger/app/bluetooth/` for Android reference implementation.

## Architecture Diagram

```
┌─────────────┐
│   Android   │◄─────BLE─────►┌─────────┐
│  (Working)  │                │   iOS   │
└──────┬──────┘                └────┬────┘
       │                            │
       │      ┌──────────────┐      │
       └─────►│   Bridgefy   │◄─────┘
              │   (Optional) │
              └──────────────┘
                Multi-hop mesh
```

## Troubleshooting

**"No devices found"**
- Android: Check Location permission + Bluetooth ON
- iOS: CoreBluetooth not implemented yet

**"Messages stuck pending"**
- No direct BLE connection
- Mesh SDK not configured
- Recipient device offline

**Build errors**
- Android: Run `./gradlew clean`
- iOS: Clean build folder (⌘⇧K)

## Documentation

- [MESH_ARCHITECTURE.md](MESH_ARCHITECTURE.md) - Full architecture details
- [BRIDGEFY_INTEGRATION.md](BRIDGEFY_INTEGRATION.md) - SDK integration guide
- [README.md](README.md) - Original project overview
- [VOICE_MESSAGING_GUIDE.md](VOICE_MESSAGING_GUIDE.md) - Audio features

## Support

File issues or ask questions: [GitHub Issues](https://github.com/yourusername/BluetoothMessenger/issues)
