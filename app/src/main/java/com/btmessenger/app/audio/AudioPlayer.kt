package com.btmessenger.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

/**
 * Audio player for voice messages
 */
class AudioPlayer(private val context: Context) {
    
    private val tag = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    
    /**
     * Play an audio file
     */
    fun play(file: File, onComplete: (() -> Unit)? = null) {
        try {
            // Stop current playback if any
            stop()
            
            currentFile = file
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                
                _duration.value = (duration / 1000)
                
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    onComplete?.invoke()
                    Log.d(tag, "Playback completed")
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                
                start()
                _isPlaying.value = true
                
                Log.d(tag, "Playing audio: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(tag, "Failed to play audio", e)
            stop()
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to play audio", e)
            stop()
        }
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            Log.d(tag, "Playback paused")
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to pause", e)
        }
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            Log.d(tag, "Playback resumed")
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to resume", e)
        }
    }
    
    /**
     * Stop playback and release resources
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: IllegalStateException) {
            Log.e(tag, "Error stopping playback", e)
        }
        
        mediaPlayer = null
        currentFile = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
        
        Log.d(tag, "Playback stopped")
    }
    
    /**
     * Seek to position in seconds
     */
    fun seekTo(seconds: Int) {
        try {
            mediaPlayer?.seekTo(seconds * 1000)
            _currentPosition.value = seconds
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to seek", e)
        }
    }
    
    /**
     * Get current playback position in seconds
     */
    fun getCurrentPosition(): Int {
        return try {
            val positionMs = mediaPlayer?.currentPosition ?: 0
            (positionMs / 1000)
        } catch (e: IllegalStateException) {
            0
        }
    }
    
    /**
     * Get audio duration in seconds
     */
    fun getAudioDuration(file: File): Int {
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            val duration = (mp.duration / 1000)
            mp.release()
            duration
        } catch (e: Exception) {
            Log.e(tag, "Failed to get audio duration", e)
            0
        }
    }
    
    /**
     * Check if currently playing a specific file
     */
    fun isPlayingFile(file: File): Boolean {
        return _isPlaying.value && currentFile?.absolutePath == file.absolutePath
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stop()
    }
}
