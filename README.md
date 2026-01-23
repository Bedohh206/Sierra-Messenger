<<<<<<< HEAD
﻿# Sierra-Messenger
=======
<<<<<<< HEAD
# SierraPulse
>>>>>>> f7a7d49 (Remove large binaries and logs from commit; keep SierraPulse rename)

A peer-to-peer messaging and file sharing Android app that works offline using Bluetooth (BLE and Classic Bluetooth) when there's no WiFi or mobile data available.

## Features

- **Offline Messaging**: Send and receive text messages without internet connection
- **Voice Messages**: Record and send short voice messages (perfect for low-literacy communities)
- **Device Discovery**: Automatically discover nearby Bluetooth devices
- **Dual Bluetooth Support**: Uses both BLE (Bluetooth Low Energy) and Classic Bluetooth for maximum compatibility
- **Image Sharing**: Share images with nearby devices (with offer/accept protocol)
- **Message Persistence**: All messages are stored locally using Room database
- **Modern UI**: Built with Jetpack Compose and Material 3 design
- **Real-time Communication**: GATT server/client for BLE and RFCOMM for Classic Bluetooth

## Architecture

### Project Structure

`
app/
 bluetooth/
    BleAdvertiser.kt       # Makes device discoverable via BLE
    BleScanner.kt          # Scans for nearby BLE devices
    GattServer.kt          # BLE GATT server for receiving connections
    GattClient.kt          # BLE GATT client for connecting to devices
    ClassicServer.kt       # Classic Bluetooth server (RFCOMM)
    ClassicClient.kt       # Classic Bluetooth client (RFCOMM)
    Protocol.kt            # Message protocol definitions
 audio/
    AudioRecorder.kt       # Records voice messages
    AudioPlayer.kt         # Plays voice messages
 data/
    AppDatabase.kt         # Room database
    entities/
       Peer.kt           # Peer device entity
       Message.kt        # Message entity
    dao/
       PeerDao.kt        # Data access for peers
       MessageDao.kt     # Data access for messages
    repository/
        MessengerRepository.kt
 ui/
    NearbyPeersScreen.kt  # Device discovery and connection screen
    ChatScreen.kt         # Chat interface
    theme/                # Material 3 theming
 security/
     Crypto.kt             # Optional encryption utilities (for v2)
`

## Message Protocol

The app uses a JSON-based message protocol for communication:

### Text Message
`json
{
  "v": 1,
  "type": "TEXT",
  "msgId": "uuid",
  "from": "peerA",
  "to": "peerB",
  "ts": 1700000000,
  "body": "Hello"
}
`

### Image Offer
`json
{
  "v": 1,
  "type": "IMAGE_OFFER",
  "msgId": "uuid",
  "fileName": "photo.jpg",
  "fileSize": 245001,
  "mime": "image/jpeg"
}
`

### Audio Offer (Voice Message)
`json
{
  "v": 1,
  "type": "AUDIO_OFFER",
  "msgId": "uuid",
  "fileName": "voice_123.3gp",
  "fileSize": 15420,
  "mime": "audio/3gpp",
  "duration": 5
}
`

### Other Message Types
- IMAGE_ACCEPT: Accept an image transfer
- IMAGE_REJECT: Reject an image transfer
- IMAGE_CHUNK: Transfer image data in chunks
- IMAGE_COMPLETE: Signal transfer completion
- AUDIO_ACCEPT: Accept an audio transfer
- AUDIO_REJECT: Reject an audio transfer
- AUDIO_CHUNK: Transfer audio data in chunks
- AUDIO_COMPLETE: Signal audio transfer completion

## Permissions

The app requires the following permissions:

### Android 12+ (API 31+)
- BLUETOOTH_SCAN: Discover nearby devices
- BLUETOOTH_ADVERTISE: Make device discoverable
- BLUETOOTH_CONNECT: Connect to devices

### Android 11 and below
- BLUETOOTH: Basic Bluetooth functionality
- BLUETOOTH_ADMIN: Administrative Bluetooth tasks
- ACCESS_FINE_LOCATION: Required for BLE scanning

### Storage
- READ_MEDIA_IMAGES (Android 13+): Access images
- READ_EXTERNAL_STORAGE (Android 12 and below): Access images
- READ_MEDIA_AUDIO (Android 13+): Access audio files
- RECORD_AUDIO: Record voice messages

## Technical Details

### Bluetooth Communication

**BLE (Bluetooth Low Energy)**
- Used for low-power device discovery and messaging
- GATT (Generic Attribute Profile) server/client architecture
- Custom service UUID for app identification
- Two characteristics: MESSAGE and TRANSFER

**Classic Bluetooth**
- Fallback for devices with poor BLE support
- RFCOMM socket communication
- SPP (Serial Port Profile) UUID
- Reliable stream-based data transfer

### Data Persistence

- **Room Database**: Local SQLite database for storing messages and peers
- **Repositories**: Clean architecture pattern for data access
- **Flow**: Reactive data streams for UI updates

### UI/UX

- **Jetpack Compose**: Modern declarative UI framework
- **Material 3**: Latest Material Design guidelines
- **Navigation**: Compose Navigation for screen transitions
- **Permissions**: Runtime permission handling with Accompanist

## Building the Project

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+ (minimum)
- Android SDK 34 (target)
- Kotlin 1.9.20+

### Build Steps

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project: Build > Make Project
4. Run on device or emulator: Run > Run 'app'

### Using Gradle Command Line

`ash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
`

## Usage

1. **Grant Permissions**: On first launch, grant all required Bluetooth and storage permissions    
2. **Enable Bluetooth**: Make sure Bluetooth is enabled on your device
3. **Make Visible**: Tap the "Hidden" FAB to make your device discoverable
4. **Scan for Devices**: Tap the search icon to start scanning for nearby devices
5. **Connect**: Tap on a discovered device to open the chat screen
6. **Send Messages**: Type and send text messages or share images
6. **Voice Messages**: Hold the microphone button to record a voice message (great for low-literacy users!)
7. **Play Voice Messages**: Tap the play button on received voice messages to listen
8. **Offline Communication**: Works completely offline, no internet required!

## Security Considerations

- **Current Version**: Messages are sent in plain text
- **Future Version**: The Crypto.kt class provides encryption utilities
- **Peer Trust**: Always verify the device you're connecting to
- **Data Privacy**: All messages are stored locally on the device

## Known Limitations

- BLE has limited range (typically 10-30 meters)
- File transfer speed depends on Bluetooth version
- Some devices may have limited BLE support
- No message encryption in current version

## Future Enhancements

- [ ] End-to-end encryption using Crypto utilities
- [ ] Group messaging support
- [ ] Video message support
- [ ] File transfer (not just images)
- [ ] Message delivery confirmation
- [ ] Read receipts
- [ ] Profile pictures
- [ ] Device pairing persistence
- [ ] Message search
- [ ] Export chat history
- [ ] Push-to-talk mode for voice
- [ ] Voice message playback speed control

## License

This project is provided as-is for educational and personal use.

## Contributing

Feel free to fork this project and submit pull requests for improvements!

## Troubleshooting

### Devices not discovering each other
- Ensure both devices have Bluetooth enabled
- Check that permissions are granted
- Try toggling "Make Visible" on both devices
- Move devices closer together

### Connection fails
- Some devices work better with Classic Bluetooth
- Try restarting Bluetooth on both devices
- Clear app data and try again

### Messages not sending
- Check connection status in the chat screen
- Verify Bluetooth permissions are granted
- Try reconnecting to the device

## Credits

Built with:
- Kotlin
- Jetpack Compose
- Room Database
- Material 3
- Bluetooth APIs

---

**Note**: This app is designed for offline peer-to-peer communication. It does not connect to the internet or send data to any servers.
=======
# Sierra-Messenger
A lightweight communication platform designed to share text messages, photos, and short videos without internet or cellular networks. 
>>>>>>> 061b8164da8280c308b6a253a55b72fbec114cc8
