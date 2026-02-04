# Bridgefy SDK Integration Guide

## Overview

This guide explains how to integrate Bridgefy mesh networking SDK into the Android and iOS apps when you obtain your API key.

## Getting Your API Key

1. Visit [Bridgefy Dashboard](https://dashboard.bridgefy.me)
2. Create an account or sign in
3. Create a new app
4. Copy the API key from the dashboard

## Android Integration

### Step 1: Add API Key

Add to `gradle.properties` (root or `~/.gradle/gradle.properties`):

```properties
BRIDGEFY_API_KEY=your-actual-api-key-here
```

### Step 2: Add SDK Dependency

Edit `app/build.gradle.kts`, add to dependencies block:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Bridgefy SDK (check latest version at https://github.com/bridgefy/sdk-android)
    implementation("me.bridgefy:android-sdk:2.0.0")
}
```

### Step 3: Sync Gradle

Run in Android Studio:
```bash
File → Sync Project with Gradle Files
```

Or via command line:
```bash
./gradlew sync
```

### Step 4: Wire the SDK

Edit `app/src/main/java/com/btmessenger/app/transport/BridgefyMeshSdkAdapter.kt`:

```kotlin
package com.btmessenger.app.transport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import me.bridgefy.sdk.client.Bridgefy
import me.bridgefy.sdk.client.BridgefyListener
import me.bridgefy.sdk.client.Message
import kotlin.coroutines.resume

class BridgefyMeshSdkAdapter(
    private val context: Context,
    private val apiKey: String
) : MeshSdkAdapter {
    private val tag = "BridgefyMeshSdk"
    private var initialized = false

    init {
        if (apiKey.isNotBlank()) {
            try {
                Bridgefy.initialize(context, apiKey, object : BridgefyListener() {
                    override fun onStarted() {
                        Log.d(tag, "Bridgefy started")
                        initialized = true
                    }

                    override fun onStartError(error: String) {
                        Log.e(tag, "Bridgefy start error: $error")
                    }

                    override fun onMessageReceived(message: Message) {
                        Log.d(tag, "Mesh message received: ${message.content}")
                        // TODO: Forward to MessageRouter for handling
                    }

                    override fun onMessageFailed(message: Message, error: String) {
                        Log.e(tag, "Mesh message failed: $error")
                    }
                })
                Bridgefy.start()
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize Bridgefy", e)
            }
        }
    }

    override val isAvailable: Boolean
        get() = initialized && Bridgefy.isStarted()

    override suspend fun send(toId: String, payload: String): Boolean {
        if (!isAvailable) return false
        
        return suspendCancellableCoroutine { continuation ->
            try {
                val message = Message.Builder()
                    .setContent(payload.toByteArray())
                    .setReceiverId(toId)
                    .build()
                
                Bridgefy.sendMessage(message)
                continuation.resume(true)
            } catch (e: Exception) {
                Log.e(tag, "Send failed", e)
                continuation.resume(false)
            }
        }
    }

    fun stop() {
        if (initialized) {
            Bridgefy.stop()
        }
    }
}
```

### Step 5: Add Permissions

Bridgefy requires these permissions in `AndroidManifest.xml`:

```xml
<!-- Already present in app -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- May need to add if using mesh over Wi-Fi -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

### Step 6: Test

1. Install on 2+ devices
2. Check logs for "Bridgefy started"
3. Send a message
4. Verify mesh routing works (check "Mesh send" logs)

## iOS Integration

### Step 1: Add SDK via CocoaPods

Create or edit `ios/Podfile`:

```ruby
platform :ios, '16.0'
use_frameworks!

target 'BluetoothMessenger' do
  pod 'BridgefySDK', '~> 2.0'
end
```

Run:
```bash
cd ios
pod install
```

Open the generated workspace (not .xcodeproj):
```bash
open BluetoothMessenger.xcworkspace
```

### Step 2: Add API Key

Edit `ios/BluetoothMessenger/Info.plist`:

```xml
<key>BridgefyAPIKey</key>
<string>your-actual-api-key-here</string>
```

### Step 3: Wire the SDK

Create `ios/BluetoothMessenger/Transport/BridgefyAdapter.swift`:

```swift
import Foundation
import BridgefySDK

final class BridgefyAdapter: ObservableObject {
    @Published private(set) var isStarted = false
    private let apiKey: String
    
    init(apiKey: String) {
        self.apiKey = apiKey
        if !apiKey.isEmpty {
            setupBridgefy()
        }
    }
    
    private func setupBridgefy() {
        Bridgefy.initialize(withApiKey: apiKey) { error in
            if let error = error {
                print("Bridgefy init error: \\(error)")
                return
            }
            
            Bridgefy.delegate = self
            Bridgefy.start { error in
                if let error = error {
                    print("Bridgefy start error: \\(error)")
                } else {
                    print("Bridgefy started")
                    DispatchQueue.main.async {
                        self.isStarted = true
                    }
                }
            }
        }
    }
    
    func send(toUserId: String, payload: String, completion: @escaping (Bool) -> Void) {
        guard isStarted, let data = payload.data(using: .utf8) else {
            completion(false)
            return
        }
        
        Bridgefy.sendMessage(data, toUser: toUserId) { error in
            completion(error == nil)
        }
    }
}

extension BridgefyAdapter: BridgefyDelegate {
    func bridgefy(_ bridgefy: Bridgefy, didReceiveMessage message: BridgefyMessage) {
        guard let payload = String(data: message.data, encoding: .utf8) else { return }
        print("Mesh message received: \\(payload)")
        // TODO: Forward to MessageRouter
    }
    
    func bridgefy(_ bridgefy: Bridgefy, didFailSendingMessage message: BridgefyMessage, withError error: Error?) {
        print("Mesh send failed: \\(error?.localizedDescription ?? "unknown")")
    }
}
```

### Step 4: Integrate with Router

Edit `ios/BluetoothMessenger/Transport/MessageRouter.swift`:

```swift
import Foundation

final class MessageRouter: ObservableObject {
    @Published private(set) var outbox: [OutboxEntry] = []
    @Published private(set) var delivered: Set<String> = []
    
    private let ackTimeout: TimeInterval = 15
    private let retryDelay: TimeInterval = 10
    private var bridgefy: BridgefyAdapter?
    
    init(bridgefyApiKey: String = "") {
        if !bridgefyApiKey.isEmpty {
            self.bridgefy = BridgefyAdapter(apiKey: bridgefyApiKey)
        }
    }

    func enqueueWithAck(msgId: String, toId: String, payload: String) {
        let entry = OutboxEntry(
            msgId: msgId,
            toId: toId,
            payload: payload,
            status: .pending,
            attempts: 0,
            lastAttemptAt: nil,
            nextAttemptAt: Date()
        )
        outbox.append(entry)
        attemptSend(msgId: msgId)
    }

    private func attemptSend(msgId: String) {
        guard let idx = outbox.firstIndex(where: { $0.msgId == msgId }) else { return }
        var entry = outbox[idx]
        entry.attempts += 1
        entry.lastAttemptAt = Date()
        entry.status = .awaitingAck
        entry.nextAttemptAt = Date().addingTimeInterval(ackTimeout)
        outbox[idx] = entry

        // Try mesh if available
        if let bridgefy = bridgefy, bridgefy.isStarted {
            bridgefy.send(toUserId: entry.toId, payload: entry.payload) { success in
                print("Mesh send result: \\(success)")
            }
        }
    }
    
    // ... rest of implementation
}
```

### Step 5: Update App Entry

Edit `ios/BluetoothMessenger/BluetoothMessengerApp.swift`:

```swift
import SwiftUI

@main
struct BluetoothMessengerApp: App {
    @StateObject private var router: MessageRouter
    
    init() {
        // Read API key from Info.plist
        let apiKey = Bundle.main.object(forInfoDictionaryKey: "BridgefyAPIKey") as? String ?? ""
        _router = StateObject(wrappedValue: MessageRouter(bridgefyApiKey: apiKey))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(router)
        }
    }
}
```

### Step 6: Test

1. Build and run on 2+ iOS devices
2. Check Xcode console for "Bridgefy started"
3. Send messages
4. Verify mesh routing in logs

## Troubleshooting

### Android: "Bridgefy not wired yet"
- Check `BRIDGEFY_API_KEY` is in gradle.properties
- Verify SDK dependency is added
- Rebuild project (`./gradlew clean build`)

### iOS: "Bridgefy init error"
- Check API key in Info.plist is correct
- Verify CocoaPods installed (`pod install`)
- Check SDK version compatibility

### "Bridgefy start error"
- Invalid API key
- Network connectivity required for first initialization
- Check SDK version matches your account tier

### Messages not routing through mesh
- Devices must be within Bluetooth range
- Mesh requires 3+ devices for multi-hop
- Check both sender and receiver have Bridgefy initialized

## Advanced Configuration

### Android: Background Service

For persistent mesh routing, run in a service:

```kotlin
class MeshService : Service() {
    private lateinit var adapter: BridgefyMeshSdkAdapter
    
    override fun onCreate() {
        super.onCreate()
        adapter = BridgefyMeshSdkAdapter(this, BuildConfig.BRIDGEFY_API_KEY)
    }
    
    override fun onDestroy() {
        adapter.stop()
        super.onDestroy()
    }
}
```

### iOS: Background Mode

Add to Info.plist for background mesh:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>
```

## Support

- Bridgefy Docs: https://docs.bridgefy.me
- Android SDK: https://github.com/bridgefy/sdk-android
- iOS SDK: https://github.com/bridgefy/sdk-ios
- Dashboard: https://dashboard.bridgefy.me

## Next Steps

After Bridgefy is working:
1. Test multi-hop routing (3+ devices)
2. Measure latency and delivery rates
3. Implement retry logic for failed mesh sends
4. Add mesh status indicators in UI
5. Configure Bridgefy settings (encryption, delivery mode, etc.)
