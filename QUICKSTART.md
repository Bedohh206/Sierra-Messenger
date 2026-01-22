# Sierra Messenger - Quick Start Guide

## What is Sierra Messenger?

Sierra Messenger is a Bluetooth-based peer-to-peer communication platform that allows you to:
- 💬 Send text messages
- 📷 Share photos
- 🎥 Transfer short videos
- 📡 Communicate without internet or cellular networks

## Quick Setup (5 minutes)

### 1. Install System Dependencies

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install python3-dev libbluetooth-dev
```

**Fedora/RHEL:**
```bash
sudo dnf install python3-devel bluez-libs-devel
```

### 2. Install Python Dependencies

```bash
cd Sierra-Messenger
pip install -r requirements.txt
```

### 3. Run Sierra Messenger

```bash
python3 -m sierra_messenger.cli
```

## Common Use Cases

### Scenario 1: Two People Chatting

**Person A (Device 1):**
```bash
$ python3 -m sierra_messenger.cli
> server
🖥️  Starting server mode...
📡 Waiting for incoming connections...
```

**Person B (Device 2):**
```bash
$ python3 -m sierra_messenger.cli
> discover
🔍 Searching for nearby devices...
Found 1 device:
  1. Device A

> connect 1
✅ Connected!

> send Hello from Device B!
✅ Message sent
```

### Scenario 2: Sending a Photo

```bash
> sendfile /path/to/photo.jpg
📤 Sending file: photo.jpg
✅ File sent successfully
```

### Scenario 3: Viewing Message History

```bash
> messages 10
📬 Last 10 messages:
  ← [2026-01-22 00:00:00] Alice
    Hello!
  → [2026-01-22 00:01:00] Me
    Hi there!
```

## Troubleshooting

### No Devices Found?
1. Ensure Bluetooth is enabled on both devices
2. Make sure the server is running on the other device
3. Try increasing discovery time: `discover` waits 8 seconds by default

### Connection Failed?
1. Verify the device is in server mode
2. Check Bluetooth range (stay within 10-100 meters)
3. Restart Bluetooth on both devices

### Permission Denied (Linux)?
```bash
sudo usermod -a -G bluetooth $USER
# Then log out and log back in
```

## Key Commands

| Command | Description |
|---------|-------------|
| `server` | Start accepting connections |
| `discover` | Find nearby devices |
| `connect <n>` | Connect to device number n |
| `send <msg>` | Send a text message |
| `sendfile <path>` | Send a file |
| `messages [n]` | View message history |
| `stats` | Show statistics |
| `help` | Show all commands |
| `quit` | Exit application |

## Best Practices

1. **Always start one device in server mode first** before connecting from another device
2. **Check connection status** before sending messages
3. **Keep devices within Bluetooth range** (typically 10-100 meters)
4. **Use descriptive device names** to easily identify devices

## Learn More

- Full documentation: [README.md](README.md)
- API reference: [API.md](API.md)
- Installation guide: [INSTALL.md](INSTALL.md)
- Example scripts: [examples/](examples/)

## Support

For issues or questions:
1. Check the troubleshooting section in README.md
2. Review the API documentation
3. Open an issue on GitHub

---

**Happy Messaging! 📱**
