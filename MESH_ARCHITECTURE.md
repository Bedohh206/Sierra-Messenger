# Bluetooth Messenger - Multi-Platform Mesh Architecture

A resilient offline messaging system using BLE for discovery/short transfers, Wi-Fi Direct for large files, and optional mesh SDK (Bridgefy) for multi-hop routing.

## Architecture Overview

### Android (Kotlin + Jetpack Compose)
- **BLE Discovery**: `BleScanner` discovers nearby devices using filtered/unfiltered scans
- **Small Messages**: BLE GATT + Classic Bluetooth RFCOMM for text/voice (< 512 bytes)
- **Large Files**: Wi-Fi Direct transport for images/videos (> 128 KB) [stub]
- **Mesh Routing**: Optional Bridgefy SDK for multi-hop message forwarding [stub]
- **Store-and-Forward**: `MessageRouter` with ACK-based retry and outbox queue
- **Database**: Room for messages, peers, groups, and outbox persistence

### iOS (Swift + SwiftUI)
- **UI**: SwiftUI with NavigationSplitView for peer list + chat
- **Protocol**: Shared JSON message format (TEXT, ACK, IMAGE_OFFER, AUDIO_OFFER, etc.)
- **Routing**: `MessageRouter` with ACK timeout and retry logic
- **Mesh**: Bridgefy iOS SDK integration point [stub]

## Transport Priority

1. **BLE** (default): Text messages, small audio files
2. **Wi-Fi Direct** (auto-escalate): Large images/videos (> 128 KB)
3. **Mesh** (fallback): Multi-hop when direct path unavailable

## Getting Started

### Android Setup

#### Prerequisites
- Android Studio Hedgehog+
- JDK 17
- Android SDK 26+ (target 35)

#### Build
```bash
cd android
./gradlew :app:assembleDebug
```

#### Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Bridgefy Integration (Optional)
1. Get API key from [Bridgefy Dashboard](https://dashboard.bridgefy.me)
2. Add to `gradle.properties`:
   ```properties
   BRIDGEFY_API_KEY=your-api-key-here
   ```
3. Add Bridgefy SDK dependency to `app/build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation("me.bridgefy:android-sdk:2.0.0") // Check latest version
   }
   ```
4. Wire SDK in `BridgefyMeshSdkAdapter.kt`:
   - Initialize Bridgefy in `init` block
   - Implement `send()` with `Bridgefy.sendMessage()`
   - Handle incoming messages via `BridgefyListener`

### iOS Setup

#### Prerequisites
- Xcode 15+
- iOS 16+ deployment target
- Swift 5.9+

#### Build
1. Open `ios/BluetoothMessenger.xcodeproj` in Xcode
2. Select target device/simulator
3. Build & Run (⌘R)

#### Bridgefy Integration (Optional)
1. Install Bridgefy iOS SDK via CocoaPods or SPM:
   ```ruby
   # Podfile
   pod 'BridgefySDK', '~> 2.0'
   ```
2. Add API key to `Info.plist`:
   ```xml
   <key>BridgefyAPIKey</key>
   <string>your-api-key-here</string>
   ```
3. Wire SDK in `MessageRouter.swift`:
   - Initialize Bridgefy in app lifecycle
   - Implement `attemptSend()` with Bridgefy mesh send
   - Handle incoming mesh messages

## Message Protocol

All messages use JSON with shared schema:

### Text Message
```json
{
  "v": 1,
  "type": "TEXT",
  "msgId": "uuid",
  "from": "device-id",
  "to": "peer-id",
  "ts": 1704700000000,
  "body": "Hello"
}
```

### ACK Message
```json
{
  "v": 1,
  "type": "ACK",
  "msgId": "uuid",
  "from": "device-id",
  "to": "peer-id",
  "ts": 1704700000000,
  "ackFor": "original-msgId"
}
```

### Image/Audio Offer
```json
{
  "v": 1,
  "type": "IMAGE_OFFER",
  "msgId": "uuid",
  "from": "device-id",
  "to": "peer-id",
  "ts": 1704700000000,
  "fileName": "photo.jpg",
  "fileSize": 245001,
  "mime": "image/jpeg"
}
```

## Store-and-Forward Flow

1. **Send**: Message enqueued to outbox with `pending` status
2. **Attempt**: Router tries BLE → Wi-Fi Direct → Mesh (based on size)
3. **Await ACK**: Status changes to `awaiting_ack` with 15s timeout
4. **Retry**: If no ACK, retry after 10s delay
5. **Delivered**: ACK received, status becomes `delivered`

## File Structure

### Android
```
app/src/main/java/com/btmessenger/app/
├── bluetooth/          # BLE scanner, advertiser, GATT, Classic BT
├── transport/          # MessageRouter, transports, mesh adapters
├── data/              # Room database, DAOs, repositories
├── ui/                # Compose screens (Chat, NearbyPeers, etc.)
└── audio/             # Voice message recording/playback
```

### iOS
```
ios/BluetoothMessenger/
├── BluetoothMessengerApp.swift  # App entry + router injection
├── ContentView.swift            # Main UI (peer list + chat)
├── Protocol.swift               # Message encoding/decoding
├── Models.swift                 # Data models (Peer, ChatMessage)
└── Transport/
    └── MessageRouter.swift      # Outbox + ACK handling
```

## Testing Without Bridgefy

Both platforms work without Bridgefy:
- **Android**: Falls back to `NoopMeshSdkAdapter` (logs "not available")
- **iOS**: Direct peer-to-peer only (no multi-hop)

Test with 2+ devices:
1. Install on all devices
2. Grant Bluetooth permissions
3. Open app → discover nearby devices
4. Send text messages (BLE transport)
5. Check message status (pending → sent → delivered)

## Roadmap

- [ ] Wire Bridgefy Android SDK (when API key available)
- [ ] Wire Bridgefy iOS SDK (when API key available)
- [ ] Implement Wi-Fi Direct file transfer (Android)
- [ ] Add CoreBluetooth peripheral/central for iOS BLE
- [ ] Cross-platform message sync (Android ↔ iOS)
- [ ] End-to-end encryption (E2EE)
- [ ] Group chat mesh routing
- [ ] Voice message mesh forwarding

## Troubleshooting

### Android: "BLE scan failed"
- Check Bluetooth permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
- Enable Location services (required for BLE scanning on Android)
- Check Bluetooth is ON

### iOS: "No peers visible"
- Bluetooth permissions not granted
- CoreBluetooth not implemented yet (add in Phase 2)

### Messages stuck "pending"
- No BLE connection to recipient
- Recipient device offline
- Mesh routing not available (needs Bridgefy)

## Contributing

1. Fork the repo
2. Create feature branch
3. Follow existing code style (Kotlin conventions for Android, Swift guidelines for iOS)
4. Test on real devices (BLE requires hardware)
5. Submit PR with clear description

## License

See [LICENSE](../LICENSE) file.
