"""
Bluetooth module for discovering nearby devices and managing connections.
"""

import bluetooth
import socket
import threading
import time
import re
from typing import List, Tuple, Optional, Callable
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# Constants for message size limits
MAX_MESSAGE_SIZE = 100 * 1024 * 1024  # 100 MB for files
MAX_TEXT_SIZE = 10 * 1024 * 1024  # 10 MB for text messages
MAX_FILENAME_LENGTH = 255  # Maximum filename length in bytes
CHUNK_SIZE = 1024 * 1024  # 1 MB chunks for file transfer


class BluetoothDevice:
    """Represents a discovered Bluetooth device."""
    
    def __init__(self, address: str, name: str):
        self.address = address
        self.name = name or "Unknown Device"
    
    def __repr__(self):
        return f"BluetoothDevice(address='{self.address}', name='{self.name}')"
    
    def __eq__(self, other):
        if isinstance(other, BluetoothDevice):
            return self.address == other.address
        return False
    
    def __hash__(self):
        return hash(self.address)


class BluetoothManager:
    """Manages Bluetooth connections and device discovery."""
    
    # UUID for Sierra Messenger service
    SERVICE_UUID = "1e0ca4ea-299d-4335-93eb-27fcfe7fa848"
    SERVICE_NAME = "Sierra Messenger"
    
    def __init__(self):
        self.server_sock = None
        self.client_sock = None
        self.connected_device = None
        self.is_server_running = False
        self.server_thread = None
        self.on_message_received = None
        self.on_file_received = None
        self.on_connection_established = None
        self.on_connection_lost = None
    
    def discover_devices(self, duration: int = 8) -> List[BluetoothDevice]:
        """
        Discover nearby Bluetooth devices.
        
        Args:
            duration: Time in seconds to search for devices
            
        Returns:
            List of discovered BluetoothDevice objects
        """
        logger.info(f"Searching for Bluetooth devices for {duration} seconds...")
        try:
            nearby_devices = bluetooth.discover_devices(
                duration=duration,
                lookup_names=True,
                flush_cache=True
            )
            
            devices = [BluetoothDevice(addr, name) for addr, name in nearby_devices]
            logger.info(f"Found {len(devices)} device(s)")
            return devices
        except Exception as e:
            logger.error(f"Error discovering devices: {e}")
            return []
    
    def start_server(self):
        """Start the Bluetooth server to accept incoming connections."""
        if self.is_server_running:
            logger.warning("Server is already running")
            return
        
        try:
            self.server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            self.server_sock.bind(("", bluetooth.PORT_ANY))
            self.server_sock.listen(1)
            
            port = self.server_sock.getsockname()[1]
            
            # Advertise the service
            bluetooth.advertise_service(
                self.server_sock,
                self.SERVICE_NAME,
                service_id=self.SERVICE_UUID,
                service_classes=[self.SERVICE_UUID, bluetooth.SERIAL_PORT_CLASS],
                profiles=[bluetooth.SERIAL_PORT_PROFILE]
            )
            
            logger.info(f"Server started on RFCOMM channel {port}")
            logger.info("Waiting for connections...")
            
            self.is_server_running = True
            self.server_thread = threading.Thread(target=self._accept_connections, daemon=True)
            self.server_thread.start()
            
        except Exception as e:
            logger.error(f"Error starting server: {e}")
            self.is_server_running = False
    
    def _accept_connections(self):
        """Accept incoming connections (runs in a separate thread)."""
        while self.is_server_running:
            try:
                self.server_sock.settimeout(1.0)
                try:
                    client_sock, client_info = self.server_sock.accept()
                    logger.info(f"Accepted connection from {client_info}")
                    
                    self.client_sock = client_sock
                    self.connected_device = BluetoothDevice(client_info[0], "Connected Device")
                    
                    if self.on_connection_established:
                        self.on_connection_established(self.connected_device)
                    
                    # Start receiving messages
                    self._receive_loop()
                    
                except socket.timeout:
                    continue
                    
            except Exception as e:
                if self.is_server_running:
                    logger.error(f"Error accepting connection: {e}")
                break
    
    def connect_to_device(self, device: BluetoothDevice) -> bool:
        """
        Connect to a remote Bluetooth device.
        
        Args:
            device: The BluetoothDevice to connect to
            
        Returns:
            True if connection successful, False otherwise
        """
        try:
            logger.info(f"Searching for service on {device.name} ({device.address})...")
            
            # Find the service
            service_matches = bluetooth.find_service(
                uuid=self.SERVICE_UUID,
                address=device.address
            )
            
            if not service_matches:
                logger.error("Could not find Sierra Messenger service on device")
                return False
            
            first_match = service_matches[0]
            port = first_match["port"]
            host = first_match["host"]
            
            logger.info(f"Connecting to {host} on port {port}...")
            
            self.client_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            self.client_sock.connect((host, port))
            self.connected_device = device
            
            logger.info("Connected successfully!")
            
            if self.on_connection_established:
                self.on_connection_established(device)
            
            # Start receiving messages in a separate thread
            receive_thread = threading.Thread(target=self._receive_loop, daemon=True)
            receive_thread.start()
            
            return True
            
        except Exception as e:
            logger.error(f"Error connecting to device: {e}")
            return False
    
    def _receive_loop(self):
        """Receive messages from the connected device (runs in a separate thread)."""
        while self.client_sock:
            try:
                # Receive message type (1 byte)
                msg_type_data = self._recv_all(1)
                if not msg_type_data:
                    break
                
                msg_type = msg_type_data[0]
                
                # Receive message length (4 bytes)
                length_data = self._recv_all(4)
                if not length_data:
                    break
                
                message_length = int.from_bytes(length_data, byteorder='big')
                
                # Validate message length based on type
                if msg_type == 0x01 and message_length > MAX_TEXT_SIZE:
                    logger.error(f"Text message too large: {message_length} bytes (max {MAX_TEXT_SIZE})")
                    break
                elif msg_type == 0x02 and message_length > MAX_MESSAGE_SIZE:
                    logger.error(f"File message too large: {message_length} bytes (max {MAX_MESSAGE_SIZE})")
                    break
                elif message_length <= 0:
                    logger.error(f"Invalid message length: {message_length}")
                    break
                
                # Receive the actual message
                message_data = self._recv_all(message_length)
                if not message_data:
                    break
                
                if msg_type == 0x01:  # Text message
                    message = message_data.decode('utf-8')
                    logger.info(f"Received text: {message}")
                    if self.on_message_received:
                        self.on_message_received(message)
                
                elif msg_type == 0x02:  # File transfer
                    # Parse filename length (first 4 bytes)
                    if len(message_data) < 4:
                        logger.error("File message too short to contain filename length")
                        break
                    
                    filename_length = int.from_bytes(message_data[:4], byteorder='big')
                    
                    # Validate filename length
                    if filename_length <= 0 or filename_length > MAX_FILENAME_LENGTH:
                        logger.error(f"Invalid filename length: {filename_length}")
                        break
                    
                    if len(message_data) < 4 + filename_length:
                        logger.error(f"Message data too short for filename (expected {4 + filename_length}, got {len(message_data)})")
                        break
                    
                    filename = message_data[4:4+filename_length].decode('utf-8')
                    file_data = message_data[4+filename_length:]
                    
                    logger.info(f"Received file: {filename} ({len(file_data)} bytes)")
                    if self.on_file_received:
                        self.on_file_received(filename, file_data)
                
            except Exception as e:
                logger.error(f"Error receiving data: {e}")
                break
        
        # Connection lost
        logger.info("Connection closed")
        if self.on_connection_lost:
            self.on_connection_lost()
        self.disconnect()
    
    def _recv_all(self, size: int) -> Optional[bytes]:
        """Receive exactly 'size' bytes from the socket."""
        data = b''
        while len(data) < size:
            try:
                packet = self.client_sock.recv(size - len(data))
                if not packet:
                    return None
                data += packet
            except Exception as e:
                logger.error(f"Error receiving data: {e}")
                return None
        return data
    
    def send_message(self, message: str) -> bool:
        """
        Send a text message to the connected device.
        
        Args:
            message: The text message to send
            
        Returns:
            True if successful, False otherwise
        """
        if not self.client_sock or not self.connected_device:
            logger.error("Not connected to any device")
            return False
        
        try:
            message_bytes = message.encode('utf-8')
            message_length = len(message_bytes)
            
            # Send: [type:1byte][length:4bytes][data]
            data = bytes([0x01]) + message_length.to_bytes(4, byteorder='big') + message_bytes
            self.client_sock.send(data)
            
            logger.info(f"Sent message: {message}")
            return True
            
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            return False
    
    def send_file(self, filepath: str) -> bool:
        """
        Send a file to the connected device.
        
        Args:
            filepath: Path to the file to send
            
        Returns:
            True if successful, False otherwise
        """
        if not self.client_sock or not self.connected_device:
            logger.error("Not connected to any device")
            return False
        
        try:
            import os
            filename = os.path.basename(filepath)
            
            # Get file size first
            file_size = os.path.getsize(filepath)
            
            # For files larger than 10MB, we should use chunking
            # For now, read the whole file but log if it's large
            if file_size > 10 * 1024 * 1024:
                logger.info(f"Sending large file ({file_size} bytes), this may take a while...")
            
            with open(filepath, 'rb') as f:
                file_data = f.read()
            
            # Construct message: [filename_length:4bytes][filename][file_data]
            message_data = (
                filename_length.to_bytes(4, byteorder='big') +
                filename_bytes +
                file_data
            )
            
            message_length = len(message_data)
            
            # Send: [type:1byte][length:4bytes][message_data]
            data = bytes([0x02]) + message_length.to_bytes(4, byteorder='big') + message_data
            
            # Send data in chunks to avoid overwhelming the socket
            total_sent = 0
            while total_sent < len(data):
                chunk = data[total_sent:total_sent + CHUNK_SIZE]
                self.client_sock.send(chunk)
                total_sent += len(chunk)
                
                # Log progress for large files
                if file_size > 10 * 1024 * 1024:
                    progress = (total_sent / len(data)) * 100
                    logger.info(f"Progress: {progress:.1f}%")
            
            logger.info(f"Sent file: {filename} ({len(file_data)} bytes)")
            return True
            
        except Exception as e:
            logger.error(f"Error sending file: {e}")
            return False
    
    def disconnect(self):
        """Disconnect from the current device."""
        if self.client_sock:
            try:
                self.client_sock.close()
            except Exception as e:
                logger.debug(f"Error closing client socket: {e}")
            self.client_sock = None
        
        self.connected_device = None
        logger.info("Disconnected")
    
    def stop_server(self):
        """Stop the Bluetooth server."""
        self.is_server_running = False
        
        if self.server_sock:
            try:
                self.server_sock.close()
            except Exception as e:
                logger.debug(f"Error closing server socket: {e}")
            self.server_sock = None
        
        if self.server_thread:
            self.server_thread.join(timeout=2)
            self.server_thread = None
        
        logger.info("Server stopped")
    
    def is_connected(self) -> bool:
        """Check if currently connected to a device."""
        return self.client_sock is not None and self.connected_device is not None
