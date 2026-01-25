package com.btmessenger.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

/**
 * Audio recorder for voice messages
 */
class AudioRecorder(private val context: Context) {
    
    private val tag = "AudioRecorder"
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration: StateFlow<Int> = _recordingDuration
    
    /**
     * Start recording audio
     * Returns the output file or null if failed
     */
    fun startRecording(): File? {
        try {
            // Create output file
            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.3gp")
            
            // Create MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
                
                startTime = System.currentTimeMillis()
                _isRecording.value = true
                
                Log.d(tag, "Recording started: ${outputFile?.absolutePath}")
            }
            
            return outputFile
        } catch (e: IOException) {
            Log.e(tag, "Failed to start recording", e)
            stopRecording()
            return null
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to start recording", e)
            stopRecording()
            return null
        }
    }
    
    /**
     * Stop recording and return the recorded file
     * Returns null if recording failed or was too short
     */
    fun stopRecording(): File? {
        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        _recordingDuration.value = duration
        
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            
            // Check if recording is at least 1 second
            if (duration < 1) {
                Log.w(tag, "Recording too short: $duration seconds")
                outputFile?.delete()
                outputFile = null
                null
            } else {
                Log.d(tag, "Recording stopped. Duration: $duration seconds")
                outputFile
            }
        } catch (e: RuntimeException) {
            Log.e(tag, "Failed to stop recording", e)
            _isRecording.value = false
            outputFile?.delete()
            outputFile = null
            null
        }
    }
    
    /**
     * Cancel recording and delete the file
     */
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            Log.e(tag, "Error canceling recording", e)
        }
        
        mediaRecorder = null
        _isRecording.value = false
        
        outputFile?.delete()
        outputFile = null
        
        Log.d(tag, "Recording canceled")
    }
    
    /**
     * Get current recording duration in seconds
     */
    fun getCurrentDuration(): Int {
        return if (_isRecording.value) {
            ((System.currentTimeMillis() - startTime) / 1000).toInt()
        } else {
            0
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (_isRecording.value) {
            cancelRecording()
        }
    }
}
