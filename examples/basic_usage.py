"""
Example script demonstrating basic usage of Sierra Messenger API.
"""

from sierra_messenger.app import SierraMessenger
from sierra_messenger.bluetooth_manager import BluetoothDevice
from sierra_messenger.message_store import Message
import time


def on_message_received(message: Message):
    """Callback when a message is received."""
    print(f"📩 New message from {message.sender}: {message.content}")


def on_file_received(filename: str, filepath: str, size: int):
    """Callback when a file is received."""
    print(f"📎 Received file: {filename} ({size} bytes) -> {filepath}")


def on_connection_established(device: BluetoothDevice):
    """Callback when connection is established."""
    print(f"✅ Connected to {device.name} ({device.address})")


def on_connection_lost():
    """Callback when connection is lost."""
    print("❌ Connection lost")


def main():
    # Initialize the application
    app = SierraMessenger(storage_dir=".")
    
    # Set up callbacks
    app.on_message_received = on_message_received
    app.on_file_received = on_file_received
    app.on_connection_established = on_connection_established
    app.on_connection_lost = on_connection_lost
    
    print("Sierra Messenger API Example\n")
    
    # Example 1: Start as server
    print("Example 1: Starting server mode...")
    app.start_server()
    print("Server started. Waiting for connections...")
    print("(In another device, run the client example to connect)\n")
    
    # Wait for a connection (in real app, this would be event-driven)
    for i in range(30):
        if app.is_connected():
            print(f"Connected to: {app.get_connected_device().name}")
            break
        time.sleep(1)
    else:
        print("No connection received in 30 seconds\n")
    
    # Example 2: Discover and connect to devices
    if not app.is_connected():
        print("\nExample 2: Discovering devices...")
        devices = app.discover_devices(duration=8)
        
        if devices:
            print(f"Found {len(devices)} device(s):")
            for i, device in enumerate(devices, 1):
                print(f"  {i}. {device.name} ({device.address})")
            
            # Connect to the first device (in real app, let user choose)
            print(f"\nConnecting to {devices[0].name}...")
            if app.connect_to_device(devices[0]):
                print("Connected!")
            else:
                print("Connection failed")
        else:
            print("No devices found")
    
    # Example 3: Send a message (if connected)
    if app.is_connected():
        print("\nExample 3: Sending a message...")
        message = "Hello from Sierra Messenger!"
        if app.send_message(message):
            print(f"✅ Sent: {message}")
        else:
            print("❌ Failed to send message")
        
        # Wait a bit for response
        time.sleep(2)
    
    # Example 4: Get message history
    print("\nExample 4: Retrieving message history...")
    messages = app.get_messages(limit=10)
    print(f"Found {len(messages)} messages:")
    for msg in reversed(messages):
        direction = "→" if msg.is_sent else "←"
        print(f"  {direction} {msg.sender}: {msg.content}")
    
    # Example 5: Get statistics
    print("\nExample 5: Statistics:")
    stats = app.get_statistics()
    print(f"  Total messages: {stats['total_messages']}")
    print(f"  Total files: {stats['total_files']}")
    
    # Cleanup
    print("\nCleaning up...")
    app.disconnect()
    app.stop_server()
    print("Done!")


if __name__ == "__main__":
    main()
