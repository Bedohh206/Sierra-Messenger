# Sierra Messenger

A lightweight Bluetooth-based peer-to-peer communication platform designed to share text messages, photos, and short videos without internet or cellular networks. Perfect for areas with limited or no network coverage.

## Features

✨ **Key Features:**
- 📱 **Peer-to-peer Bluetooth connectivity** - Direct device-to-device communication
- 💬 **Text messaging** - Send and receive text messages
- 📷 **Photo sharing** - Share photos with connected devices
- 🎥 **Video sharing** - Transfer short videos
- 💾 **Message persistence** - All messages and files are stored locally
- 🔍 **Message search** - Search through your message history
- 📊 **Statistics** - Track sent/received messages and files
- 🔒 **No internet required** - Works completely offline using Bluetooth

## Requirements

- Python 3.8 or higher
- Bluetooth adapter on your device
- Linux (recommended) or Windows with Bluetooth support

### Python Dependencies

- **PyBluez** 0.23 - Bluetooth connectivity
- **Pillow** >= 10.3.0 - Image processing (with security fixes)
- **cryptography** >= 42.0.4 - Security utilities (with security fixes)

### System Dependencies

**On Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install python3-dev libbluetooth-dev
```

**On Fedora/RHEL:**
```bash
sudo dnf install python3-devel bluez-libs-devel
```

**On Windows:**
- Windows 10 or later with Bluetooth support
- Microsoft Visual C++ 14.0 or greater

## Installation

### Option 1: Install from source

```bash
# Clone the repository
git clone https://github.com/Bedohh206/Sierra-Messenger.git
cd Sierra-Messenger

# Install dependencies
pip install -r requirements.txt

# Install the package
pip install -e .
```

### Option 2: Quick setup

```bash
# Install dependencies
pip install PyBluez Pillow cryptography

# Run directly
python -m sierra_messenger.cli
```

## Usage

### Starting Sierra Messenger

```bash
sierra-messenger
```

Or run directly with Python:
```bash
python -m sierra_messenger.cli
```

### Available Commands

Once the application is running, you can use these commands:

- `discover` - Search for nearby Bluetooth devices
- `connect <n>` - Connect to device number n from the discovery list
- `server` - Start server mode to accept incoming connections
- `disconnect` - Disconnect from the current device
- `send <message>` - Send a text message
- `sendfile <path>` - Send a file (photo or video)
- `messages [n]` - Show last n messages (default: 20)
- `files` - Show received files
- `search <query>` - Search for messages containing query
- `stats` - Display messaging statistics
- `clear` - Clear all stored data
- `help` - Show help message
- `quit` - Exit the application

### Quick Start Guide

**Device A (Server mode):**
```
> server
🖥️  Starting server mode...
📡 Waiting for incoming connections...
```

**Device B (Client mode):**
```
> discover
🔍 Searching for nearby Bluetooth devices...
Found 2 device(s):
  1. Device A
     Address: XX:XX:XX:XX:XX:XX

> connect 1
🔗 Connecting to Device A...
✅ Connected!

> send Hello from Device B!
✅ Message sent
```

**Sending a file:**
```
> sendfile /path/to/photo.jpg
📤 Sending file: photo.jpg (1234567 bytes)...
✅ File sent successfully
```

## Architecture

Sierra Messenger consists of several key components:

### Core Modules

1. **bluetooth_manager.py** - Handles Bluetooth device discovery, connections, and data transfer
2. **message_store.py** - Manages message and file persistence using SQLite
3. **app.py** - Main application controller coordinating all components
4. **cli.py** - Command-line interface for user interaction

### Communication Protocol

The application uses a simple binary protocol over Bluetooth RFCOMM:

```
Message format: [type:1byte][length:4bytes][data:variable]

Type values:
- 0x01: Text message
- 0x02: File transfer

For file transfers:
data = [filename_length:4bytes][filename:variable][file_data:variable]
```

## File Storage

- **Messages**: Stored in SQLite database (`messages.db`)
- **Received files**: Saved in `received_files/` directory
- **Database schema**: Messages and files tables with metadata

## Use Cases

🏔️ **Hiking and Outdoor Activities**
- Stay connected with your group when there's no cell service

🏗️ **Construction Sites**
- Share updates and photos in areas with poor connectivity

🚢 **Maritime Communication**
- Keep in touch on boats or remote islands

🏕️ **Emergency Situations**
- Maintain communication when infrastructure is down

🌍 **Remote Areas**
- Connect in regions with limited network infrastructure

## Security Considerations

- Messages are transmitted over Bluetooth without encryption by default
- For sensitive communications, consider using the platform in trusted environments
- Keep Bluetooth discoverable mode off when not actively connecting
- Only connect to known and trusted devices

## Limitations

- **Range**: Bluetooth typically works within 10-100 meters depending on device class
- **Speed**: File transfer speed depends on Bluetooth version (typically 1-3 MB/s)
- **File size**: Recommended maximum file size is 100 MB
- **Connections**: Supports one active connection at a time

## Troubleshooting

**"No devices found" during discovery:**
- Ensure Bluetooth is enabled on both devices
- Make sure the other device is discoverable
- Check that you have proper permissions to access Bluetooth

**"Connection failed":**
- Verify the device is running Sierra Messenger in server mode
- Ensure devices are within Bluetooth range
- Try restarting Bluetooth on both devices

**Permission errors on Linux:**
```bash
sudo usermod -a -G bluetooth $USER
# Log out and log back in
```

## Development

### Running Tests

```bash
# Install development dependencies
pip install pytest pytest-cov

# Run tests
pytest tests/
```

### Project Structure

```
Sierra-Messenger/
├── sierra_messenger/
│   ├── __init__.py
│   ├── bluetooth_manager.py    # Bluetooth connectivity
│   ├── message_store.py         # Data persistence
│   ├── app.py                   # Main application
│   └── cli.py                   # CLI interface
├── requirements.txt
├── setup.py
├── README.md
└── LICENSE
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Alpha B Kamara**

## Acknowledgments

- Built with [PyBluez](https://github.com/pybluez/pybluez) for Bluetooth connectivity
- Uses SQLite for local data storage
- Inspired by the need for offline communication solutions

## Roadmap

Future enhancements planned:
- [ ] End-to-end encryption for messages
- [ ] Group messaging support
- [ ] Voice message support
- [ ] GUI application
- [ ] Mobile app versions (Android/iOS)
- [ ] Message delivery confirmations
- [ ] Profile pictures and user profiles

---

**Note**: This is a community project aimed at providing communication solutions for areas with limited connectivity. For production use in critical situations, please conduct thorough testing. 
