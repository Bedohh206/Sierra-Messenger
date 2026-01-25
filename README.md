<<<<<<< HEAD
# Bluetooth Messenger

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

```
app/
├── bluetooth/
│   ├── BleAdvertiser.kt       # Makes device discoverable via BLE
│   ├── BleScanner.kt          # Scans for nearby BLE devices
│   ├── GattServer.kt          # BLE GATT server for receiving connections
│   ├── GattClient.kt          # BLE GATT client for connecting to devices
│   ├── ClassicServer.kt       # Classic Bluetooth server (RFCOMM)
│   ├── ClassicClient.kt       # Classic Bluetooth client (RFCOMM)
│   └── Protocol.kt            # Message protocol definitions
├── audio/
│   ├── AudioRecorder.kt       # Records voice messages
│   └── AudioPlayer.kt         # Plays voice messages
├── data/
│   ├── AppDatabase.kt         # Room database
│   ├── entities/
│   │   ├── Peer.kt           # Peer device entity
│   │   └── Message.kt        # Message entity
│   ├── dao/
│   │   ├── PeerDao.kt        # Data access for peers
│   │   └── MessageDao.kt     # Data access for messages
│   └── repository/
│       └── MessengerRepository.kt
├── ui/
│   ├── NearbyPeersScreen.kt  # Device discovery and connection screen
│   ├── ChatScreen.kt         # Chat interface
│   └── theme/                # Material 3 theming
└── security/
    └── Crypto.kt             # Optional encryption utilities (for v2)
```

## Message Protocol

The app uses a JSON-based message protocol for communication:

### Text Message
```json
{
  "v": 1,
  "type": "TEXT",
  "msgId": "uuid",
  "from": "peerA",
  "to": "peerB",
  "ts": 1700000000,
  "body": "Hello"
}
```

### Image Offer
```json
{
  "v": 1,
  "type": "IMAGE_OFFER",
  "msgId": "uuid",
  "fileName": "photo.jpg",
  "fileSize": 245001,
  "mime": "image/jpeg"
}
```

### Audio Offer (Voice Message)
```json
{
  "v": 1,
  "type": "AUDIO_OFFER",
  "msgId": "uuid",
  "fileName": "voice_123.3gp",
  "fileSize": 15420,
  "mime": "audio/3gpp",
  "duration": 5
}
```

### Other Message Types
- `IMAGE_ACCEPT`: Accept an image transfer
- `IMAGE_REJECT`: Reject an image transfer
- `IMAGE_CHUNK`: Transfer image data in chunks
- `IMAGE_COMPLETE`: Signal transfer completion
- `AUDIO_ACCEPT`: Accept an audio transfer
- `AUDIO_REJECT`: Reject an audio transfer
 # Bluetooth Messenger

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

```
app/
├── bluetooth/
│   ├── BleAdvertiser.kt       # Makes device discoverable via BLE
│   ├── BleScanner.kt          # Scans for nearby BLE devices
│   ├── GattServer.kt          # BLE GATT server for receiving connections
│   ├── GattClient.kt          # BLE GATT client for connecting to devices
│   ├── ClassicServer.kt       # Classic Bluetooth server (RFCOMM)
│   ├── ClassicClient.kt       # Classic Bluetooth client (RFCOMM)
│   └── Protocol.kt            # Message protocol definitions
├── audio/
│   ├── AudioRecorder.kt       # Records voice messages
│   └── AudioPlayer.kt         # Plays voice messages
├── data/
│   ├── AppDatabase.kt         # Room database
│   ├── entities/
│   │   ├── Peer.kt           # Peer device entity
│   │   └── Message.kt        # Message entity
│   ├── dao/
│   │   ├── PeerDao.kt        # Data access for peers
│   │   └── MessageDao.kt     # Data access for messages
│   └── repository/
│       └── MessengerRepository.kt
├── ui/
│   ├── NearbyPeersScreen.kt  # Device discovery and connection screen
│   ├── ChatScreen.kt         # Chat interface
│   └── theme/                # Material 3 theming
└── security/
    └── Crypto.kt             # Optional encryption utilities (for v2)
```
- **Navigation**: Compose Navigation for screen transitions
