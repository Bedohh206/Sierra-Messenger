"""
Basic structure and import tests for Sierra Messenger.
These tests verify the package structure without requiring Bluetooth hardware.
"""

import unittest
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))


class TestImports(unittest.TestCase):
    """Test that all modules can be imported."""
    
    def test_import_main_package(self):
        """Test importing the main package."""
        import sierra_messenger
        self.assertEqual(sierra_messenger.__version__, "1.0.0")
        self.assertEqual(sierra_messenger.__author__, "Alpha B Kamara")
    
    def test_import_bluetooth_manager(self):
        """Test importing bluetooth_manager module."""
        from sierra_messenger.bluetooth_manager import BluetoothManager, BluetoothDevice
        
        # Test BluetoothDevice creation
        device = BluetoothDevice("00:11:22:33:44:55", "Test Device")
        self.assertEqual(device.address, "00:11:22:33:44:55")
        self.assertEqual(device.name, "Test Device")
    
    def test_import_message_store(self):
        """Test importing message_store module."""
        from sierra_messenger.message_store import MessageStore, Message
        
        # Test Message creation
        msg = Message("Hello", "Alice", is_sent=True)
        self.assertEqual(msg.content, "Hello")
        self.assertEqual(msg.sender, "Alice")
        self.assertTrue(msg.is_sent)
    
    def test_import_app(self):
        """Test importing app module."""
        from sierra_messenger.app import SierraMessenger
        # Don't instantiate to avoid Bluetooth requirements


class TestMessageStore(unittest.TestCase):
    """Test MessageStore functionality."""
    
    def setUp(self):
        """Set up test database."""
        from sierra_messenger.message_store import MessageStore, Message
        import tempfile
        
        self.MessageStore = MessageStore
        self.Message = Message
        
        # Use tempfile for cross-platform compatibility
        self.temp_dir = tempfile.TemporaryDirectory()
        self.test_db = os.path.join(self.temp_dir.name, 'test_messages.db')
        
        self.store = MessageStore(self.test_db)
    
    def tearDown(self):
        """Clean up test database."""
        self.temp_dir.cleanup()
    
    def test_save_and_retrieve_message(self):
        """Test saving and retrieving a message."""
        msg = self.Message("Test message", "Alice", is_sent=True)
        msg_id = self.store.save_message(msg)
        
        self.assertIsNotNone(msg_id)
        self.assertGreater(msg_id, 0)
        
        # Retrieve messages
        messages = self.store.get_messages(limit=10)
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0].content, "Test message")
        self.assertEqual(messages[0].sender, "Alice")
    
    def test_multiple_messages(self):
        """Test storing multiple messages."""
        for i in range(5):
            msg = self.Message(f"Message {i}", f"User{i}", is_sent=(i % 2 == 0))
            self.store.save_message(msg)
        
        messages = self.store.get_messages(limit=10)
        self.assertEqual(len(messages), 5)
    
    def test_search_messages(self):
        """Test searching for messages."""
        self.store.save_message(self.Message("Hello world", "Alice", is_sent=True))
        self.store.save_message(self.Message("Goodbye world", "Bob", is_sent=False))
        self.store.save_message(self.Message("Test message", "Charlie", is_sent=True))
        
        results = self.store.search_messages("world")
        self.assertEqual(len(results), 2)
        
        results = self.store.search_messages("Test")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].sender, "Charlie")
    
    def test_get_statistics(self):
        """Test getting statistics."""
        self.store.save_message(self.Message("Sent 1", "Me", is_sent=True))
        self.store.save_message(self.Message("Sent 2", "Me", is_sent=True))
        self.store.save_message(self.Message("Received 1", "Alice", is_sent=False))
        
        stats = self.store.get_statistics()
        self.assertEqual(stats['total_messages'], 3)
        self.assertEqual(stats['sent_messages'], 2)
        self.assertEqual(stats['received_messages'], 1)


class TestBluetoothDevice(unittest.TestCase):
    """Test BluetoothDevice class."""
    
    def test_device_creation(self):
        """Test creating a BluetoothDevice."""
        from sierra_messenger.bluetooth_manager import BluetoothDevice
        
        device = BluetoothDevice("AA:BB:CC:DD:EE:FF", "My Device")
        self.assertEqual(device.address, "AA:BB:CC:DD:EE:FF")
        self.assertEqual(device.name, "My Device")
    
    def test_device_equality(self):
        """Test device equality comparison."""
        from sierra_messenger.bluetooth_manager import BluetoothDevice
        
        device1 = BluetoothDevice("AA:BB:CC:DD:EE:FF", "Device 1")
        device2 = BluetoothDevice("AA:BB:CC:DD:EE:FF", "Device 2")
        device3 = BluetoothDevice("11:22:33:44:55:66", "Device 3")
        
        self.assertEqual(device1, device2)  # Same address
        self.assertNotEqual(device1, device3)  # Different address
    
    def test_device_hash(self):
        """Test device hashing for use in sets/dicts."""
        from sierra_messenger.bluetooth_manager import BluetoothDevice
        
        device1 = BluetoothDevice("AA:BB:CC:DD:EE:FF", "Device 1")
        device2 = BluetoothDevice("AA:BB:CC:DD:EE:FF", "Device 2")
        
        device_set = {device1, device2}
        self.assertEqual(len(device_set), 1)  # Should be deduplicated


class TestMessage(unittest.TestCase):
    """Test Message class."""
    
    def test_message_creation(self):
        """Test creating a Message."""
        from sierra_messenger.message_store import Message
        
        msg = Message("Hello!", "Alice", is_sent=True)
        self.assertEqual(msg.content, "Hello!")
        self.assertEqual(msg.sender, "Alice")
        self.assertTrue(msg.is_sent)
        self.assertIsNotNone(msg.timestamp)
    
    def test_message_to_dict(self):
        """Test converting message to dictionary."""
        from sierra_messenger.message_store import Message
        
        msg = Message("Test", "Bob", message_id=42, is_sent=False)
        msg_dict = msg.to_dict()
        
        self.assertEqual(msg_dict['id'], 42)
        self.assertEqual(msg_dict['content'], "Test")
        self.assertEqual(msg_dict['sender'], "Bob")
        self.assertFalse(msg_dict['is_sent'])


if __name__ == '__main__':
    unittest.main()
