"""
Message storage and management module.
"""

import os
import json
import sqlite3
from datetime import datetime
from typing import List, Dict, Optional
import logging

logger = logging.getLogger(__name__)


class Message:
    """Represents a message in the system."""
    
    def __init__(self, content: str, sender: str, timestamp: Optional[str] = None, 
                 message_id: Optional[int] = None, is_sent: bool = False):
        self.id = message_id
        self.content = content
        self.sender = sender
        self.timestamp = timestamp or datetime.now().isoformat()
        self.is_sent = is_sent
    
    def to_dict(self) -> Dict:
        """Convert message to dictionary."""
        return {
            'id': self.id,
            'content': self.content,
            'sender': self.sender,
            'timestamp': self.timestamp,
            'is_sent': self.is_sent
        }
    
    def __repr__(self):
        direction = "Sent" if self.is_sent else "Received"
        return f"[{self.timestamp}] {direction} from {self.sender}: {self.content}"


class MessageStore:
    """Manages message persistence using SQLite."""
    
    def __init__(self, db_path: str = "messages.db"):
        self.db_path = db_path
        self._init_database()
    
    def _init_database(self):
        """Initialize the database schema."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Create messages table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                sender TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                is_sent INTEGER NOT NULL DEFAULT 0
            )
        ''')
        
        # Create files table
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                filepath TEXT NOT NULL,
                sender TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                is_sent INTEGER NOT NULL DEFAULT 0,
                file_size INTEGER NOT NULL
            )
        ''')
        
        conn.commit()
        conn.close()
        logger.info(f"Database initialized at {self.db_path}")
    
    def save_message(self, message: Message) -> int:
        """
        Save a message to the database.
        
        Args:
            message: The Message object to save
            
        Returns:
            The ID of the saved message
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute('''
            INSERT INTO messages (content, sender, timestamp, is_sent)
            VALUES (?, ?, ?, ?)
        ''', (message.content, message.sender, message.timestamp, int(message.is_sent)))
        
        message_id = cursor.lastrowid
        conn.commit()
        conn.close()
        
        logger.info(f"Saved message with ID {message_id}")
        return message_id
    
    def get_messages(self, limit: int = 100, offset: int = 0) -> List[Message]:
        """
        Retrieve messages from the database.
        
        Args:
            limit: Maximum number of messages to retrieve
            offset: Number of messages to skip
            
        Returns:
            List of Message objects
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT id, content, sender, timestamp, is_sent
            FROM messages
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        ''', (limit, offset))
        
        rows = cursor.fetchall()
        conn.close()
        
        messages = [
            Message(
                content=row[1],
                sender=row[2],
                timestamp=row[3],
                message_id=row[0],
                is_sent=bool(row[4])
            )
            for row in rows
        ]
        
        return messages
    
    def search_messages(self, query: str) -> List[Message]:
        """
        Search for messages containing the query string.
        
        Args:
            query: The search term
            
        Returns:
            List of matching Message objects
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT id, content, sender, timestamp, is_sent
            FROM messages
            WHERE content LIKE ?
            ORDER BY timestamp DESC
        ''', (f'%{query}%',))
        
        rows = cursor.fetchall()
        conn.close()
        
        messages = [
            Message(
                content=row[1],
                sender=row[2],
                timestamp=row[3],
                message_id=row[0],
                is_sent=bool(row[4])
            )
            for row in rows
        ]
        
        return messages
    
    def delete_message(self, message_id: int) -> bool:
        """
        Delete a message from the database.
        
        Args:
            message_id: The ID of the message to delete
            
        Returns:
            True if successful, False otherwise
        """
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('DELETE FROM messages WHERE id = ?', (message_id,))
            
            conn.commit()
            conn.close()
            
            logger.info(f"Deleted message with ID {message_id}")
            return True
        except Exception as e:
            logger.error(f"Error deleting message: {e}")
            return False
    
    def save_file_record(self, filename: str, filepath: str, sender: str, 
                        is_sent: bool, file_size: int) -> int:
        """
        Save a file transfer record to the database.
        
        Args:
            filename: Name of the file
            filepath: Path where the file is stored
            sender: Device that sent the file
            is_sent: Whether this device sent the file
            file_size: Size of the file in bytes
            
        Returns:
            The ID of the saved record
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        timestamp = datetime.now().isoformat()
        
        cursor.execute('''
            INSERT INTO files (filename, filepath, sender, timestamp, is_sent, file_size)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (filename, filepath, sender, timestamp, int(is_sent), file_size))
        
        file_id = cursor.lastrowid
        conn.commit()
        conn.close()
        
        logger.info(f"Saved file record with ID {file_id}")
        return file_id
    
    def get_files(self, limit: int = 100, offset: int = 0) -> List[Dict]:
        """
        Retrieve file transfer records from the database.
        
        Args:
            limit: Maximum number of records to retrieve
            offset: Number of records to skip
            
        Returns:
            List of file record dictionaries
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT id, filename, filepath, sender, timestamp, is_sent, file_size
            FROM files
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        ''', (limit, offset))
        
        rows = cursor.fetchall()
        conn.close()
        
        files = [
            {
                'id': row[0],
                'filename': row[1],
                'filepath': row[2],
                'sender': row[3],
                'timestamp': row[4],
                'is_sent': bool(row[5]),
                'file_size': row[6]
            }
            for row in rows
        ]
        
        return files
    
    def get_statistics(self) -> Dict:
        """
        Get statistics about messages and files.
        
        Returns:
            Dictionary with various statistics
        """
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Get message counts
        cursor.execute('SELECT COUNT(*) FROM messages WHERE is_sent = 0')
        received_messages = cursor.fetchone()[0]
        
        cursor.execute('SELECT COUNT(*) FROM messages WHERE is_sent = 1')
        sent_messages = cursor.fetchone()[0]
        
        # Get file counts
        cursor.execute('SELECT COUNT(*) FROM files WHERE is_sent = 0')
        received_files = cursor.fetchone()[0]
        
        cursor.execute('SELECT COUNT(*) FROM files WHERE is_sent = 1')
        sent_files = cursor.fetchone()[0]
        
        # Get total file size
        cursor.execute('SELECT COALESCE(SUM(file_size), 0) FROM files')
        total_file_size = cursor.fetchone()[0]
        
        conn.close()
        
        return {
            'total_messages': received_messages + sent_messages,
            'received_messages': received_messages,
            'sent_messages': sent_messages,
            'total_files': received_files + sent_files,
            'received_files': received_files,
            'sent_files': sent_files,
            'total_file_size': total_file_size
        }
    
    def clear_all_data(self) -> bool:
        """
        Clear all messages and file records from the database.
        
        Returns:
            True if successful, False otherwise
        """
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('DELETE FROM messages')
            cursor.execute('DELETE FROM files')
            
            conn.commit()
            conn.close()
            
            logger.info("Cleared all data from database")
            return True
        except Exception as e:
            logger.error(f"Error clearing data: {e}")
            return False
