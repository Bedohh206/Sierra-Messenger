# TODO: Bridgefy Mesh SDK Integration

## Current Status: ✅ Ready for SDK

Both Android and iOS have mesh infrastructure in place. When you obtain Bridgefy API keys:

### Android Integration Points

**File:** `app/src/main/java/com/btmessenger/app/transport/BridgefyMeshSdkAdapter.kt`

```kotlin
// TODO: Wire real Bridgefy SDK once dependency is added
// 1. Add to gradle.properties: BRIDGEFY_API_KEY=your-key
// 2. Add to app/build.gradle.kts: implementation("me.bridgefy:android-sdk:2.0.0")
// 3. Initialize Bridgefy.initialize(context, apiKey, listener)
// 4. Implement send() with Bridgefy.sendMessage()
// 5. Handle onMessageReceived() callback
```

**Current behavior:** Logs "Bridgefy SDK not wired yet" and returns `false` from `send()`.

**What works:** Graceful fallback to BLE-only mode.

### iOS Integration Points

**File:** `ios/BluetoothMessenger/Transport/MessageRouter.swift`

```swift
// TODO: Wire Bridgefy iOS SDK
// 1. Add Podfile: pod 'BridgefySDK', '~> 2.0'
// 2. Add Info.plist: BridgefyAPIKey = your-key
// 3. Create BridgefyAdapter with Bridgefy.initialize()
// 4. Implement attemptSend() with bridgefy.send()
// 5. Handle BridgefyDelegate callbacks
```

**Current behavior:** Direct peer-to-peer only (no mesh routing).

**What works:** UI, protocol, and message queueing ready for mesh.

## Wi-Fi Direct Integration

**File:** `app/src/main/java/com/btmessenger/app/transport/WifiDirectTransport.kt`

```kotlin
// TODO: Implement Wi-Fi Direct for large file transfers
// 1. Use WifiP2pManager for group formation
// 2. Create socket connection between group owner and client
// 3. Stream file chunks with progress callbacks
// 4. Handle group teardown after transfer
```

**Trigger:** Automatically used when file size > 128 KB.

**Current behavior:** Logs "not implemented yet" and returns `false`.

## CoreBluetooth (iOS BLE)

**Files to create:**
- `ios/BluetoothMessenger/Bluetooth/BLECentralManager.swift`
- `ios/BluetoothMessenger/Bluetooth/BLEPeripheralManager.swift`

```swift
// TODO: Implement iOS BLE discovery and messaging
// 1. CBCentralManager for scanning
// 2. CBPeripheralManager for advertising
// 3. Service UUID: 00001234-0000-1000-8000-00805f9b34fb
// 4. Characteristic UUID: 00001235-0000-1000-8000-00805f9b34fb
// 5. Wire to MessageRouter for send/receive
```

**What's missing:** iOS can't discover Android devices or send/receive via BLE yet.

**Priority:** HIGH (core functionality)

## AVFoundation Voice (iOS)

**Files to create:**
- `ios/BluetoothMessenger/Audio/AudioRecorder.swift`
- `ios/BluetoothMessenger/Audio/AudioPlayer.swift`

```swift
// TODO: Implement iOS voice messaging
// 1. AVAudioRecorder with .m4a format
// 2. AVAudioPlayer for playback
// 3. Match Android duration extraction
// 4. Wire to ContentView UI
```

**What's missing:** iOS can't record or play voice messages.

**Priority:** MEDIUM (feature parity)

## Testing Checklist

Once Bridgefy is integrated:

- [ ] 2-device Android ↔ Android mesh routing
- [ ] 2-device iOS ↔ iOS mesh routing
- [ ] 2-device Android ↔ iOS mesh routing
- [ ] 3-device multi-hop (A → B → C)
- [ ] Message delivery rate > 95% within 30s
- [ ] ACK round-trip time < 2s
- [ ] Mesh fallback when direct BLE unavailable

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| BLE Discovery | < 5s | Time to find nearby device |
| Message Latency | < 1s | Direct BLE delivery |
| Mesh Latency | < 5s | Single-hop mesh |
| ACK Timeout | 15s | Configurable in MessageRouter |
| Retry Delay | 10s | Exponential backoff optional |
| Delivery Rate | > 95% | Within 3 retries |

## Documentation Status

✅ **MESH_ARCHITECTURE.md** - Complete architecture overview  
✅ **BRIDGEFY_INTEGRATION.md** - Step-by-step SDK integration  
✅ **MESH_QUICK_START.md** - Quick setup guide  
✅ **TODO.md** - This file  

## Questions to Answer

1. **Bridgefy Tier**: Which plan? (free tier has device limits)
2. **iOS Bundle ID**: Confirm `com.btmessenger.ios` for provisioning
3. **Encryption**: E2EE on top of Bridgefy? (Bridgefy has built-in encryption)
4. **Background Mode**: Persistent mesh when app backgrounded?
5. **Battery Impact**: Acceptable for 24/7 mesh operation?

## Estimated Effort

| Task | Effort | Dependencies |
|------|--------|--------------|
| Bridgefy Android | 2-4 hours | API key + SDK docs |
| Bridgefy iOS | 2-4 hours | API key + SDK docs |
| Wi-Fi Direct | 8-12 hours | Android P2P API knowledge |
| CoreBluetooth iOS | 8-12 hours | iOS BLE experience |
| AVFoundation Audio | 4-6 hours | iOS audio APIs |
| Cross-platform testing | 4-8 hours | Multiple devices |

**Total:** ~30-45 hours to full feature parity.

## Next Steps

1. Obtain Bridgefy API key from dashboard
2. Follow [BRIDGEFY_INTEGRATION.md](BRIDGEFY_INTEGRATION.md)
3. Test on real devices (simulator won't work for BLE/mesh)
4. Iterate on retry logic and timeouts
5. Add mesh status indicators to UI

## Support Resources

- Bridgefy Docs: https://docs.bridgefy.me
- Android BLE Guide: https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview
- iOS CoreBluetooth: https://developer.apple.com/documentation/corebluetooth
- Wi-Fi Direct: https://developer.android.com/guide/topics/connectivity/wifip2p
