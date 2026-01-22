"""
Main application controller for Sierra Messenger.
"""

import os
import time
from typing import Optional, Callable
import logging

from .bluetooth_manager import BluetoothManager, BluetoothDevice
from .message_store import MessageStore, Message

logger = logging.getLogger(__name__)


class SierraMessenger:
    """
    Main application class that coordinates Bluetooth connectivity and messaging.
    """
    
    def __init__(self, storage_dir: str = "."):
        """
        Initialize Sierra Messenger.
        
        Args:
            storage_dir: Directory for storing messages and files
        """
        self.storage_dir = storage_dir
        self.received_files_dir = os.path.join(storage_dir, "received_files")
        
        # Create necessary directories
        os.makedirs(self.received_files_dir, exist_ok=True)
        
        # Initialize components
        db_path = os.path.join(storage_dir, "messages.db")
        self.message_store = MessageStore(db_path)
        self.bluetooth_manager = BluetoothManager()
        
        # Set up callbacks
        self.bluetooth_manager.on_message_received = self._handle_received_message
        self.bluetooth_manager.on_file_received = self._handle_received_file
        self.bluetooth_manager.on_connection_established = self._handle_connection_established
        self.bluetooth_manager.on_connection_lost = self._handle_connection_lost
        
        # Application callbacks (can be set by UI or CLI)
        self.on_message_received: Optional[Callable] = None
        self.on_file_received: Optional[Callable] = None
        self.on_connection_established: Optional[Callable] = None
        self.on_connection_lost: Optional[Callable] = None
        
        self.device_name = "Sierra Messenger"
        logger.info("Sierra Messenger initialized")
    
    def _handle_received_message(self, message_text: str):
        """Handle a received text message."""
        sender = "Unknown"
        if self.bluetooth_manager.connected_device:
            sender = self.bluetooth_manager.connected_device.name
        
        message = Message(
            content=message_text,
            sender=sender,
            is_sent=False
        )
        
        self.message_store.save_message(message)
        
        if self.on_message_received:
            self.on_message_received(message)
    
    def _handle_received_file(self, filename: str, file_data: bytes):
        """Handle a received file."""
        sender = "Unknown"
        if self.bluetooth_manager.connected_device:
            sender = self.bluetooth_manager.connected_device.name
        
        # Sanitize filename to prevent path traversal attacks
        # Remove any directory components and keep only the basename
        filename = os.path.basename(filename)
        
        # Remove any potentially dangerous characters
        # Allow only alphanumeric, dots, dashes, underscores
        import re
        filename = re.sub(r'[^\w\-_\. ]', '_', filename)
        
        # Prevent hidden files
        if filename.startswith('.'):
            filename = '_' + filename
        
        # Ensure filename is not empty after sanitization
        if not filename or filename.strip() == '':
            filename = 'received_file'
        
        # Save the file
        filepath = os.path.join(self.received_files_dir, filename)
        
        # Handle duplicate filenames
        base, ext = os.path.splitext(filename)
        counter = 1
        while os.path.exists(filepath):
            filepath = os.path.join(self.received_files_dir, f"{base}_{counter}{ext}")
            counter += 1
        
        try:
            with open(filepath, 'wb') as f:
                f.write(file_data)
            
            self.message_store.save_file_record(
                filename=os.path.basename(filepath),
                filepath=filepath,
                sender=sender,
                is_sent=False,
                file_size=len(file_data)
            )
            
            if self.on_file_received:
                self.on_file_received(filename, filepath, len(file_data))
                
            logger.info(f"Saved received file to {filepath}")
            
        except Exception as e:
            logger.error(f"Error saving received file: {e}")
    
    def _handle_connection_established(self, device: BluetoothDevice):
        """Handle connection establishment."""
        logger.info(f"Connected to {device.name} ({device.address})")
        if self.on_connection_established:
            self.on_connection_established(device)
    
    def _handle_connection_lost(self):
        """Handle connection loss."""
        logger.info("Connection lost")
        if self.on_connection_lost:
            self.on_connection_lost()
    
    def start_server(self):
        """Start the Bluetooth server to accept incoming connections."""
        self.bluetooth_manager.start_server()
    
    def stop_server(self):
        """Stop the Bluetooth server."""
        self.bluetooth_manager.stop_server()
    
    def discover_devices(self, duration: int = 8):
        """
        Discover nearby Bluetooth devices.
        
        Args:
            duration: Time in seconds to search
            
        Returns:
            List of discovered BluetoothDevice objects
        """
        return self.bluetooth_manager.discover_devices(duration)
    
    def connect_to_device(self, device: BluetoothDevice) -> bool:
        """
        Connect to a remote device.
        
        Args:
            device: The BluetoothDevice to connect to
            
        Returns:
            True if successful, False otherwise
        """
        return self.bluetooth_manager.connect_to_device(device)
    
    def disconnect(self):
        """Disconnect from the current device."""
        self.bluetooth_manager.disconnect()
    
    def send_message(self, message_text: str) -> bool:
        """
        Send a text message.
        
        Args:
            message_text: The message to send
            
        Returns:
            True if successful, False otherwise
        """
        if not self.bluetooth_manager.is_connected():
            logger.error("Not connected to any device")
            return False
        
        success = self.bluetooth_manager.send_message(message_text)
        
        if success:
            sender = "Me"
            message = Message(
                content=message_text,
                sender=sender,
                is_sent=True
            )
            self.message_store.save_message(message)
        
        return success
    
    def send_file(self, filepath: str) -> bool:
        """
        Send a file.
        
        Args:
            filepath: Path to the file to send
            
        Returns:
            True if successful, False otherwise
        """
        if not self.bluetooth_manager.is_connected():
            logger.error("Not connected to any device")
            return False
        
        if not os.path.exists(filepath):
            logger.error(f"File not found: {filepath}")
            return False
        
        # Check file size
        file_size = os.path.getsize(filepath)
        max_size = 100 * 1024 * 1024  # 100 MB
        if file_size > max_size:
            logger.error(f"File too large: {file_size} bytes (max {max_size} bytes)")
            return False
        
        success = self.bluetooth_manager.send_file(filepath)
        
        if success:
            filename = os.path.basename(filepath)
            self.message_store.save_file_record(
                filename=filename,
                filepath=filepath,
                sender="Me",
                is_sent=True,
                file_size=file_size
            )
        
        return success
    
    def is_connected(self) -> bool:
        """Check if currently connected to a device."""
        return self.bluetooth_manager.is_connected()
    
    def get_connected_device(self) -> Optional[BluetoothDevice]:
        """Get the currently connected device."""
        return self.bluetooth_manager.connected_device
    
    def get_messages(self, limit: int = 100, offset: int = 0):
        """Get stored messages."""
        return self.message_store.get_messages(limit, offset)
    
    def get_files(self, limit: int = 100, offset: int = 0):
        """Get stored file records."""
        return self.message_store.get_files(limit, offset)
    
    def search_messages(self, query: str):
        """Search for messages containing the query."""
        return self.message_store.search_messages(query)
    
    def get_statistics(self):
        """Get messaging statistics."""
        return self.message_store.get_statistics()
    
    def clear_all_data(self):
        """Clear all stored messages and file records."""
        return self.message_store.clear_all_data()
