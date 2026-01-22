# Installation Guide

## Prerequisites

Before installing Sierra Messenger, ensure you have:

1. **Python 3.8 or higher**
   ```bash
   python3 --version
   ```

2. **Bluetooth adapter** on your device
   - Most laptops and computers have built-in Bluetooth
   - For desktop computers without Bluetooth, you'll need a USB Bluetooth adapter

3. **Operating System Support**
   - Linux (Ubuntu, Debian, Fedora, etc.) - Recommended
   - Windows 10/11 with Bluetooth support
   - macOS (with some limitations)

## System Dependencies

### Ubuntu/Debian

```bash
sudo apt-get update
sudo apt-get install python3-dev python3-pip libbluetooth-dev
```

### Fedora/RHEL/CentOS

```bash
sudo dnf install python3-devel python3-pip bluez-libs-devel
```

### Arch Linux

```bash
sudo pacman -S python python-pip bluez bluez-utils
```

### Windows

1. Install Python 3.8+ from [python.org](https://www.python.org/downloads/)
2. Install Microsoft Visual C++ Build Tools
3. Ensure Bluetooth is enabled in Windows settings

### macOS

```bash
brew install python@3.9
```

Note: macOS has limited PyBluez support. Consider using Linux for best experience.

## Installation Steps

### Method 1: Install from Source (Recommended)

```bash
# Clone the repository
git clone https://github.com/Bedohh206/Sierra-Messenger.git
cd Sierra-Messenger

# Create a virtual environment (optional but recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Install Sierra Messenger
pip install -e .
```

### Method 2: Install Dependencies Only

If you just want to run the application without installing it:

```bash
cd Sierra-Messenger
pip install PyBluez Pillow cryptography
```

## Verification

Test that the installation was successful:

```bash
# If installed via setup.py
sierra-messenger --version

# Or run directly
python3 -m sierra_messenger.cli
```

You should see the Sierra Messenger welcome screen.

## Troubleshooting

### "bluetooth module not found"

On Linux, install the Bluetooth development libraries:
```bash
sudo apt-get install libbluetooth-dev  # Ubuntu/Debian
sudo dnf install bluez-libs-devel      # Fedora/RHEL
```

Then reinstall PyBluez:
```bash
pip install --upgrade --force-reinstall PyBluez
```

### Permission Denied on Linux

Add your user to the bluetooth group:
```bash
sudo usermod -a -G bluetooth $USER
```

Log out and log back in for changes to take effect.

### Windows Build Errors

Install Microsoft Visual C++ 14.0 or greater:
1. Download from [visualstudio.microsoft.com](https://visualstudio.microsoft.com/visual-cpp-build-tools/)
2. Install "Desktop development with C++" workload

### Bluetooth Not Working

1. Ensure Bluetooth is enabled:
   ```bash
   # Linux
   sudo systemctl start bluetooth
   sudo systemctl enable bluetooth
   
   # Check status
   sudo systemctl status bluetooth
   ```

2. Make device discoverable:
   ```bash
   # Linux
   bluetoothctl
   power on
   discoverable on
   ```

## Next Steps

After successful installation:
1. Read the [README.md](README.md) for usage instructions
2. Try the examples in the `examples/` directory
3. Run `sierra-messenger` to start the CLI application

## Uninstallation

To remove Sierra Messenger:

```bash
pip uninstall sierra-messenger PyBluez Pillow cryptography
```

To also remove data:
```bash
rm -rf ~/.sierra-messenger  # If you configured a custom data directory
rm messages.db
rm -rf received_files/
```
