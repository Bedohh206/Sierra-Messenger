# API Documentation

## Overview

Sierra Messenger provides a simple Python API for building Bluetooth-based messaging applications.

## Core Classes

### SierraMessenger

Main application class that provides high-level messaging functionality.

```python
from sierra_messenger.app import SierraMessenger

app = SierraMessenger(storage_dir=".")
```

#### Constructor

```python
SierraMessenger(storage_dir: str = ".")
```

**Parameters:**
- `storage_dir` (str): Directory for storing messages and files. Default is current directory.

#### Methods

##### start_server()

Start the Bluetooth server to accept incoming connections.

```python
app.start_server()
```

##### stop_server()

Stop the Bluetooth server.

```python
app.stop_server()
```

##### discover_devices(duration: int = 8) -> List[BluetoothDevice]

Discover nearby Bluetooth devices.

```python
devices = app.discover_devices(duration=10)
for device in devices:
    print(f"{device.name} - {device.address}")
```

**Parameters:**
- `duration` (int): Time in seconds to search. Default is 8 seconds.

**Returns:**
- List of `BluetoothDevice` objects

##### connect_to_device(device: BluetoothDevice) -> bool

Connect to a remote Bluetooth device.

```python
success = app.connect_to_device(device)
if success:
    print("Connected!")
```

**Parameters:**
- `device` (BluetoothDevice): Device to connect to

**Returns:**
- `True` if successful, `False` otherwise

##### disconnect()

Disconnect from the current device.

```python
app.disconnect()
```

##### send_message(message_text: str) -> bool

Send a text message to the connected device.

```python
success = app.send_message("Hello, world!")
```

**Parameters:**
- `message_text` (str): The message to send

**Returns:**
- `True` if successful, `False` otherwise

##### send_file(filepath: str) -> bool

Send a file to the connected device.

```python
success = app.send_file("/path/to/photo.jpg")
```

**Parameters:**
- `filepath` (str): Path to the file to send

**Returns:**
- `True` if successful, `False` otherwise

##### is_connected() -> bool

Check if currently connected to a device.

```python
if app.is_connected():
    print("Connected")
```

##### get_connected_device() -> Optional[BluetoothDevice]

Get the currently connected device.

```python
device = app.get_connected_device()
if device:
    print(f"Connected to {device.name}")
```

##### get_messages(limit: int = 100, offset: int = 0) -> List[Message]

Retrieve stored messages.

```python
messages = app.get_messages(limit=20)
for msg in messages:
    print(f"{msg.sender}: {msg.content}")
```

**Parameters:**
- `limit` (int): Maximum number of messages to retrieve
- `offset` (int): Number of messages to skip

**Returns:**
- List of `Message` objects

##### search_messages(query: str) -> List[Message]

Search for messages containing the query string.

```python
results = app.search_messages("hello")
```

**Parameters:**
- `query` (str): Search term

**Returns:**
- List of matching `Message` objects

##### get_files(limit: int = 100, offset: int = 0) -> List[Dict]

Get stored file transfer records.

```python
files = app.get_files()
for f in files:
    print(f"{f['filename']} - {f['file_size']} bytes")
```

##### get_statistics() -> Dict

Get messaging statistics.

```python
stats = app.get_statistics()
print(f"Total messages: {stats['total_messages']}")
```

**Returns:**
Dictionary with keys:
- `total_messages` (int)
- `received_messages` (int)
- `sent_messages` (int)
- `total_files` (int)
- `received_files` (int)
- `sent_files` (int)
- `total_file_size` (int)

#### Callbacks

Set callback functions to handle events:

```python
def on_message_received(message: Message):
    print(f"New message: {message.content}")

def on_file_received(filename: str, filepath: str, size: int):
    print(f"Received file: {filename}")

def on_connection_established(device: BluetoothDevice):
    print(f"Connected to {device.name}")

def on_connection_lost():
    print("Connection lost")

app.on_message_received = on_message_received
app.on_file_received = on_file_received
app.on_connection_established = on_connection_established
app.on_connection_lost = on_connection_lost
```

### BluetoothDevice

Represents a Bluetooth device.

```python
from sierra_messenger.bluetooth_manager import BluetoothDevice

device = BluetoothDevice(address="XX:XX:XX:XX:XX:XX", name="My Device")
```

**Attributes:**
- `address` (str): Bluetooth MAC address
- `name` (str): Device name

### Message

Represents a text message.

```python
from sierra_messenger.message_store import Message

message = Message(
    content="Hello!",
    sender="Alice",
    is_sent=False
)
```

**Attributes:**
- `id` (Optional[int]): Database ID
- `content` (str): Message text
- `sender` (str): Sender name
- `timestamp` (str): ISO format timestamp
- `is_sent` (bool): Whether this device sent the message

**Methods:**

```python
# Convert to dictionary
msg_dict = message.to_dict()
```

## Usage Examples

### Example 1: Simple Server

```python
from sierra_messenger.app import SierraMessenger
import time

app = SierraMessenger()

# Set up callback
def on_message(msg):
    print(f"Received: {msg.content}")

app.on_message_received = on_message

# Start server
app.start_server()
print("Server running...")

# Keep running
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    app.stop_server()
```

### Example 2: Simple Client

```python
from sierra_messenger.app import SierraMessenger

app = SierraMessenger()

# Discover devices
devices = app.discover_devices()
if devices:
    # Connect to first device
    app.connect_to_device(devices[0])
    
    # Send a message
    app.send_message("Hello from Python!")
    
    # Disconnect
    app.disconnect()
```

### Example 3: File Transfer

```python
from sierra_messenger.app import SierraMessenger

app = SierraMessenger()

# ... connect to device ...

# Send a photo
app.send_file("/path/to/photo.jpg")

# Send a video
app.send_file("/path/to/video.mp4")
```

### Example 4: Message History

```python
from sierra_messenger.app import SierraMessenger

app = SierraMessenger()

# Get recent messages
messages = app.get_messages(limit=50)

for msg in messages:
    direction = "Sent" if msg.is_sent else "Received"
    print(f"[{msg.timestamp}] {direction}: {msg.content}")
```

## Error Handling

The API methods return boolean values for success/failure. Always check return values:

```python
# Check if connected before sending
if not app.is_connected():
    print("Error: Not connected")
    exit(1)

# Check if message was sent successfully
if not app.send_message("Hello"):
    print("Error: Failed to send message")
```

## Thread Safety

- The `BluetoothManager` class uses threading internally for accepting connections and receiving messages
- Callbacks are called from background threads
- When using callbacks with UI frameworks, ensure thread-safe updates

## Best Practices

1. **Always disconnect when done:**
   ```python
   try:
       # ... your code ...
   finally:
       app.disconnect()
       app.stop_server()
   ```

2. **Check connection status:**
   ```python
   if app.is_connected():
       app.send_message("Hello")
   ```

3. **Handle callbacks:**
   ```python
   # Set up callbacks before connecting
   app.on_message_received = handle_message
   app.start_server()
   ```

4. **Validate file paths:**
   ```python
   import os
   if os.path.exists(filepath):
       app.send_file(filepath)
   ```

5. **Use appropriate discovery duration:**
   ```python
   # Shorter for quick scans
   devices = app.discover_devices(duration=5)
   
   # Longer for thorough discovery
   devices = app.discover_devices(duration=15)
   ```

## Limitations

- Maximum file size: 100 MB (configurable in source)
- Single connection at a time
- Bluetooth range: typically 10-100 meters
- Transfer speed: depends on Bluetooth version (1-3 MB/s typical)

## See Also

- [README.md](README.md) - General information
- [INSTALL.md](INSTALL.md) - Installation instructions
- [examples/](examples/) - Example scripts
