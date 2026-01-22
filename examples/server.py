"""
Server example - Run this on one device to accept connections.
"""

from sierra_messenger.app import SierraMessenger
from sierra_messenger.bluetooth_manager import BluetoothDevice
from sierra_messenger.message_store import Message
import time


def main():
    print("="*60)
    print("SIERRA MESSENGER - SERVER MODE")
    print("="*60)
    
    # Initialize the application
    app = SierraMessenger(storage_dir=".")
    
    # Set up callbacks
    def on_message(msg: Message):
        print(f"\n📩 Message from {msg.sender}: {msg.content}")
    
    def on_file(filename: str, filepath: str, size: int):
        print(f"\n📎 Received file: {filename} ({size} bytes)")
    
    def on_connected(device: BluetoothDevice):
        print(f"\n✅ {device.name} connected!")
    
    def on_disconnected():
        print("\n❌ Device disconnected")
    
    app.on_message_received = on_message
    app.on_file_received = on_file
    app.on_connection_established = on_connected
    app.on_connection_lost = on_disconnected
    
    # Start the server
    print("\n🖥️  Starting server...")
    app.start_server()
    print("📡 Waiting for incoming connections...")
    print("   (Other devices can now discover and connect to this device)")
    print("\nPress Ctrl+C to stop\n")
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n\nShutting down...")
        app.stop_server()
        app.disconnect()
        print("Goodbye!")


if __name__ == "__main__":
    main()
