"""
Client example - Run this on another device to connect to a server.
"""

from sierra_messenger.app import SierraMessenger
from sierra_messenger.bluetooth_manager import BluetoothDevice
from sierra_messenger.message_store import Message
import time


def main():
    print("="*60)
    print("SIERRA MESSENGER - CLIENT MODE")
    print("="*60)
    
    # Initialize the application
    app = SierraMessenger(storage_dir=".")
    
    # Set up callbacks
    def on_message(msg: Message):
        print(f"\n📩 Message from {msg.sender}: {msg.content}")
    
    def on_file(filename: str, filepath: str, size: int):
        print(f"\n📎 Received file: {filename}")
    
    def on_connected(device: BluetoothDevice):
        print(f"\n✅ Connected to {device.name}!")
    
    def on_disconnected():
        print("\n❌ Disconnected")
    
    app.on_message_received = on_message
    app.on_file_received = on_file
    app.on_connection_established = on_connected
    app.on_connection_lost = on_disconnected
    
    # Discover devices
    print("\n🔍 Discovering nearby devices...")
    devices = app.discover_devices(duration=8)
    
    if not devices:
        print("❌ No devices found")
        print("   Make sure the server is running and discoverable")
        return
    
    print(f"\n✅ Found {len(devices)} device(s):\n")
    for i, device in enumerate(devices, 1):
        print(f"  {i}. {device.name}")
        print(f"     Address: {device.address}")
    
    # Select device to connect to
    print("\nEnter device number to connect (or 0 to exit): ", end="")
    try:
        choice = int(input())
        if choice == 0:
            print("Exiting...")
            return
        
        if choice < 1 or choice > len(devices):
            print("❌ Invalid choice")
            return
        
        device = devices[choice - 1]
    except ValueError:
        print("❌ Invalid input")
        return
    
    # Connect to the selected device
    print(f"\n🔗 Connecting to {device.name}...")
    if not app.connect_to_device(device):
        print("❌ Connection failed")
        return
    
    print("✅ Connected!")
    
    # Interactive messaging
    print("\nYou can now send messages. Type 'quit' to exit.\n")
    
    try:
        while app.is_connected():
            print("> ", end="", flush=True)
            message = input()
            
            if message.lower() == 'quit':
                break
            
            if message.strip():
                if app.send_message(message):
                    print("✅ Sent")
                else:
                    print("❌ Failed to send")
            
    except KeyboardInterrupt:
        print("\n")
    
    # Cleanup
    print("Disconnecting...")
    app.disconnect()
    print("Goodbye!")


if __name__ == "__main__":
    main()
