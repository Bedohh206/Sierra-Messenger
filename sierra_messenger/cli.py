#!/usr/bin/env python3
"""
Command-line interface for Sierra Messenger.
"""

import sys
import os
import time
import argparse
from typing import Optional

from .app import SierraMessenger
from .bluetooth_manager import BluetoothDevice
from .message_store import Message


class CLI:
    """Command-line interface for Sierra Messenger."""
    
    def __init__(self):
        self.app = SierraMessenger()
        self.running = True
        
        # Set up callbacks
        self.app.on_message_received = self._on_message_received
        self.app.on_file_received = self._on_file_received
        self.app.on_connection_established = self._on_connection_established
        self.app.on_connection_lost = self._on_connection_lost
    
    def _on_message_received(self, message: Message):
        """Callback for received messages."""
        print(f"\n📩 New message from {message.sender}: {message.content}")
        self._show_prompt()
    
    def _on_file_received(self, filename: str, filepath: str, size: int):
        """Callback for received files."""
        print(f"\n📎 Received file: {filename} ({size} bytes)")
        print(f"   Saved to: {filepath}")
        self._show_prompt()
    
    def _on_connection_established(self, device: BluetoothDevice):
        """Callback for connection establishment."""
        print(f"\n✅ Connected to {device.name} ({device.address})")
        self._show_prompt()
    
    def _on_connection_lost(self):
        """Callback for connection loss."""
        print("\n❌ Connection lost")
        self._show_prompt()
    
    def _show_prompt(self):
        """Show the command prompt."""
        if self.app.is_connected():
            device = self.app.get_connected_device()
            print(f"\n[Connected to {device.name}] > ", end="", flush=True)
        else:
            print("\n[Disconnected] > ", end="", flush=True)
    
    def print_header(self):
        """Print the application header."""
        print("\n" + "="*60)
        print("          SIERRA MESSENGER")
        print("    Bluetooth P2P Communication Platform")
        print("="*60 + "\n")
    
    def print_help(self):
        """Print available commands."""
        print("\nAvailable commands:")
        print("  discover         - Discover nearby Bluetooth devices")
        print("  connect <n>      - Connect to device number <n> from discovery list")
        print("  disconnect       - Disconnect from current device")
        print("  server           - Start server mode (wait for connections)")
        print("  send <message>   - Send a text message")
        print("  sendfile <path>  - Send a file")
        print("  messages [n]     - Show last n messages (default: 20)")
        print("  files            - Show received files")
        print("  search <query>   - Search for messages")
        print("  stats            - Show messaging statistics")
        print("  clear            - Clear all stored data")
        print("  help             - Show this help message")
        print("  quit             - Exit the application")
        print()
    
    def cmd_discover(self):
        """Discover nearby devices."""
        print("🔍 Searching for nearby Bluetooth devices...")
        devices = self.app.discover_devices(duration=8)
        
        if not devices:
            print("❌ No devices found")
            return
        
        print(f"\n✅ Found {len(devices)} device(s):\n")
        self.last_discovered_devices = devices
        for i, device in enumerate(devices, 1):
            print(f"  {i}. {device.name}")
            print(f"     Address: {device.address}\n")
    
    def cmd_connect(self, args):
        """Connect to a device."""
        if not hasattr(self, 'last_discovered_devices'):
            print("❌ Please run 'discover' first")
            return
        
        if not args:
            print("❌ Usage: connect <device_number>")
            return
        
        try:
            device_num = int(args[0])
            if device_num < 1 or device_num > len(self.last_discovered_devices):
                print(f"❌ Invalid device number. Choose 1-{len(self.last_discovered_devices)}")
                return
            
            device = self.last_discovered_devices[device_num - 1]
            print(f"🔗 Connecting to {device.name}...")
            
            if self.app.connect_to_device(device):
                print(f"✅ Connected!")
            else:
                print("❌ Connection failed")
                
        except ValueError:
            print("❌ Invalid device number")
    
    def cmd_disconnect(self):
        """Disconnect from current device."""
        if not self.app.is_connected():
            print("❌ Not connected to any device")
            return
        
        self.app.disconnect()
        print("✅ Disconnected")
    
    def cmd_server(self):
        """Start server mode."""
        print("🖥️  Starting server mode...")
        print("📡 Waiting for incoming connections...")
        self.app.start_server()
        print("✅ Server started (will accept connections in background)")
    
    def cmd_send(self, args):
        """Send a text message."""
        if not self.app.is_connected():
            print("❌ Not connected to any device")
            return
        
        if not args:
            print("❌ Usage: send <message>")
            return
        
        message = ' '.join(args)
        if self.app.send_message(message):
            print(f"✅ Message sent: {message}")
        else:
            print("❌ Failed to send message")
    
    def cmd_sendfile(self, args):
        """Send a file."""
        if not self.app.is_connected():
            print("❌ Not connected to any device")
            return
        
        if not args:
            print("❌ Usage: sendfile <filepath>")
            return
        
        filepath = ' '.join(args)
        
        if not os.path.exists(filepath):
            print(f"❌ File not found: {filepath}")
            return
        
        file_size = os.path.getsize(filepath)
        print(f"📤 Sending file: {os.path.basename(filepath)} ({file_size} bytes)...")
        
        if self.app.send_file(filepath):
            print("✅ File sent successfully")
        else:
            print("❌ Failed to send file")
    
    def cmd_messages(self, args):
        """Show recent messages."""
        limit = 20
        if args:
            try:
                limit = int(args[0])
            except ValueError:
                print("❌ Invalid number")
                return
        
        messages = self.app.get_messages(limit=limit)
        
        if not messages:
            print("📭 No messages yet")
            return
        
        print(f"\n📬 Last {len(messages)} message(s):\n")
        for msg in reversed(messages):
            direction = "→" if msg.is_sent else "←"
            print(f"  {direction} [{msg.timestamp[:19]}] {msg.sender}")
            print(f"    {msg.content}\n")
    
    def cmd_files(self):
        """Show received files."""
        files = self.app.get_files()
        
        if not files:
            print("📭 No files yet")
            return
        
        print(f"\n📎 Files ({len(files)}):\n")
        for f in files:
            direction = "Sent" if f['is_sent'] else "Received"
            size_mb = f['file_size'] / (1024 * 1024)
            print(f"  {direction}: {f['filename']}")
            print(f"  Size: {size_mb:.2f} MB | {f['timestamp'][:19]}")
            print(f"  Path: {f['filepath']}\n")
    
    def cmd_search(self, args):
        """Search for messages."""
        if not args:
            print("❌ Usage: search <query>")
            return
        
        query = ' '.join(args)
        messages = self.app.search_messages(query)
        
        if not messages:
            print(f"❌ No messages found matching '{query}'")
            return
        
        print(f"\n🔍 Found {len(messages)} message(s) matching '{query}':\n")
        for msg in reversed(messages):
            direction = "→" if msg.is_sent else "←"
            print(f"  {direction} [{msg.timestamp[:19]}] {msg.sender}")
            print(f"    {msg.content}\n")
    
    def cmd_stats(self):
        """Show statistics."""
        stats = self.app.get_statistics()
        
        print("\n📊 Statistics:\n")
        print(f"  Total Messages: {stats['total_messages']}")
        print(f"    - Received: {stats['received_messages']}")
        print(f"    - Sent: {stats['sent_messages']}")
        print()
        print(f"  Total Files: {stats['total_files']}")
        print(f"    - Received: {stats['received_files']}")
        print(f"    - Sent: {stats['sent_files']}")
        print()
        size_mb = stats['total_file_size'] / (1024 * 1024)
        print(f"  Total File Size: {size_mb:.2f} MB")
        print()
    
    def cmd_clear(self):
        """Clear all data."""
        response = input("⚠️  Are you sure you want to clear all data? (yes/no): ")
        if response.lower() == 'yes':
            if self.app.clear_all_data():
                print("✅ All data cleared")
            else:
                print("❌ Failed to clear data")
        else:
            print("❌ Cancelled")
    
    def process_command(self, command_line: str):
        """Process a command."""
        if not command_line.strip():
            return
        
        parts = command_line.strip().split()
        command = parts[0].lower()
        args = parts[1:]
        
        if command == 'help':
            self.print_help()
        elif command == 'discover':
            self.cmd_discover()
        elif command == 'connect':
            self.cmd_connect(args)
        elif command == 'disconnect':
            self.cmd_disconnect()
        elif command == 'server':
            self.cmd_server()
        elif command == 'send':
            self.cmd_send(args)
        elif command == 'sendfile':
            self.cmd_sendfile(args)
        elif command == 'messages':
            self.cmd_messages(args)
        elif command == 'files':
            self.cmd_files()
        elif command == 'search':
            self.cmd_search(args)
        elif command == 'stats':
            self.cmd_stats()
        elif command == 'clear':
            self.cmd_clear()
        elif command in ['quit', 'exit']:
            self.running = False
        else:
            print(f"❌ Unknown command: {command}")
            print("   Type 'help' for available commands")
    
    def run(self):
        """Run the CLI."""
        self.print_header()
        print("Type 'help' for available commands\n")
        
        try:
            while self.running:
                self._show_prompt()
                try:
                    command = input()
                    self.process_command(command)
                except EOFError:
                    break
                except KeyboardInterrupt:
                    print("\n\nUse 'quit' to exit")
                    continue
        finally:
            print("\n👋 Shutting down...")
            self.app.stop_server()
            self.app.disconnect()
            print("Goodbye!")


def main():
    """Main entry point for the CLI."""
    parser = argparse.ArgumentParser(
        description="Sierra Messenger - Bluetooth P2P Communication Platform"
    )
    parser.add_argument(
        '--version',
        action='version',
        version='Sierra Messenger 1.0.0'
    )
    
    args = parser.parse_args()
    
    cli = CLI()
    cli.run()


if __name__ == '__main__':
    main()
