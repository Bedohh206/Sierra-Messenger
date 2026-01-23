# Quick Start Guide - Voice Messaging Feature

## 🚀 Get Started in 5 Minutes

### Prerequisites
- Android Studio Hedgehog or later
- 2 Android devices (API 26+)
- USB cables for both devices
- Bluetooth capability on both devices

# Step 1: Open the Project
```bash
# Open Android Studio
# File → Open → Navigate to C:\SierraPulse
# Wait for Gradle sync to complete
```

### Step 2: Build & Install
```bash
# Connect both devices via USB
# Enable USB Debugging on both
# In Android Studio:
# Run → Select Device → Install on both devices
```

### Step 3: Grant Permissions
On both devices:
1. Open SierraPulse app
2. Grant Bluetooth permissions
3. Grant RECORD_AUDIO permission
4. Grant storage permissions (for audio)
5. Enable Bluetooth if not already on

### Step 4: Test Voice Messaging

#### Device A (Sender):
1. Tap "Hidden" → becomes "Visible"
2. Wait for Device B to connect

#### Device B (Receiver):
1. Tap search icon 🔍
2. See Device A in list
3. Tap on Device A
4. Wait for "Connected" status

#### Send First Voice Message:
1. On Device B, tap microphone icon 🎤
2. Speak: "Hello, this is a test voice message"
3. See timer: 0:01, 0:02, 0:03...
4. Tap Send button ➤
5. Voice message appears in chat

#### Play Voice Message:
1. On Device A, see incoming voice message
2. Tap Play button ▶️
3. Listen to audio
4. Tap Pause ⏸️ to pause (optional)

### Step 5: Verify Everything Works
- ✅ Recording shows timer
- ✅ File saves successfully
- ✅ Message transfers via Bluetooth
- ✅ Playback works on receiver
- ✅ Duration displayed correctly
- ✅ Multiple messages work

---

## 🧪 Testing Scenarios

### Basic Tests
```
✓ Record 1-second message (minimum)
✓ Record 5-second message (typical)
✓ Record 30-second message (long)
✓ Cancel recording mid-way
✓ Send multiple voice messages
✓ Play while receiving new messages
✓ Pause and resume playback
```

### Edge Cases
```
✓ Record with permission denied
✓ Send with Bluetooth disconnected
✓ Play with file missing
✓ Record with low storage
✓ Multiple recordings in quick succession
✓ Switch between text and voice
```

### Network Conditions
```
✓ Test at 1 meter distance
✓ Test at 10 meters distance
✓ Test through one wall
✓ Test in crowded Bluetooth area
✓ Test with airplane mode ON
✓ Test with WiFi/data OFF
```

---

## 🐛 Common Issues & Solutions

### Issue: "Can't start recording"
**Symptom**: Microphone button doesn't work
**Solution**: 
```kotlin
// Check permission in Settings → Apps → Bluetooth Messenger → Permissions
// Ensure RECORD_AUDIO is granted
// Restart app and try again
```

### Issue: "Recording too short"
**Symptom**: Message not sent after recording
**Solution**: 
```
Record for at least 1 second before releasing
See minimum duration check in AudioRecorder.kt:

if (duration < 1) {
    // Recording rejected
}
```

### Issue: "Voice message not playing"
**Symptom**: Tap play button, nothing happens
**Solution**:
```kotlin
// Check logcat for errors:
adb logcat | grep AudioPlayer

// Common cause: File path incorrect
// Verify filePath in database:
SELECT filePath FROM messages WHERE type = 'AUDIO_OFFER';
```

### Issue: "Bluetooth transfer fails"
**Symptom**: Voice message stuck "sending"
**Solution**:
```
1. Check connection status (top bar)
2. Verify both devices connected
3. Try reconnecting
4. Check file size isn't too large
5. Try Classic Bluetooth instead of BLE
```

---

## 📝 Code Examples

### Send a Voice Message Programmatically
```kotlin
// In ChatScreen or ViewModel

suspend fun sendVoiceMessage() {
    // Start recording
    val audioFile = audioRecorder.startRecording()
    
    // Wait 5 seconds (or user input)
    delay(5000)
    
    // Stop recording
    val recordedFile = audioRecorder.stopRecording()
    
    if (recordedFile != null) {
        // Send via Bluetooth
        sendAudio(
            context = context,
            audioFile = recordedFile,
            peer = currentPeer,
            gattClient = gattClient,
            classicClient = classicClient,
            repository = repository,
            connectionType = "BLE"
        )
    }
}
```

### Play a Voice Message Programmatically
```kotlin
// In ChatScreen or MessageBubble

fun playVoiceMessage(message: Message) {
    val audioFile = File(message.filePath ?: return)
    
    if (audioFile.exists()) {
        audioPlayer.play(audioFile) {
            // On completion callback
            Log.d("Voice", "Playback completed")
        }
    }
}
```

### Create Custom Audio Offer
```kotlin
// In Protocol.kt or custom message creator

val audioMessage = Protocol.Message(
    v = 1,
    type = Protocol.TYPE_AUDIO_OFFER,
    msgId = UUID.randomUUID().toString(),
    from = Build.MODEL,
    to = peerId,
    ts = System.currentTimeMillis(),
    fileName = "voice_${System.currentTimeMillis()}.3gp",
    fileSize = audioFile.length(),
    mime = "audio/3gpp",
    duration = 8 // seconds
)

val json = Gson().toJson(audioMessage)
// Send json via Bluetooth
```

---

## 🔧 Customization Options

### Change Audio Quality
```kotlin
// In AudioRecorder.kt

// Current: AMR-NB (8kHz, ~12 kbps)
setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

// Higher quality option:
setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
// Note: Larger file sizes
```

### Adjust Maximum Duration
```kotlin
// In AudioRecorder.kt

fun startRecording(maxDurationSeconds: Int = 60): File? {
    mediaRecorder?.apply {
        // ... setup code ...
        setMaxDuration(maxDurationSeconds * 1000)
        // ... start recording ...
    }
}
```

### Custom Recording UI
```kotlin
// In ChatScreen.kt, modify recording indicator

Surface(
    color = MaterialTheme.colorScheme.primaryContainer, // Change color
    modifier = Modifier.fillMaxWidth()
) {
    Row {
        // Add waveform animation
        WaveformAnimation(amplitude = audioRecorder.getAmplitude())
        
        // Custom timer format
        Text("Recording: ${formatDuration(recordingDuration)}")
        
        // Add "Slide to cancel" gesture
        SwipeToCancel(onCancel = { audioRecorder.cancelRecording() })
    }
}
```

---

## 📊 Performance Monitoring

### Check Audio File Sizes
```bash
# Via ADB
adb shell du -sh /data/data/com.btmessenger.app/files/audio/
```

### Monitor Recording Performance
```kotlin
// In AudioRecorder.kt, add logging

override fun startRecording(): File? {
    val startTime = System.currentTimeMillis()
    
    // ... recording code ...
    
    val endTime = System.currentTimeMillis()
    Log.d("Performance", "Recording init time: ${endTime - startTime}ms")
}
```

### Track Bluetooth Transfer Speed
```kotlin
// In GattClient.kt or ClassicClient.kt

val startTime = System.currentTimeMillis()
val sent = sendMessage(jsonMessage)
val endTime = System.currentTimeMillis()

val transferTime = endTime - startTime
val fileSize = audioFile.length()
val speed = fileSize / (transferTime / 1000.0) // bytes per second

Log.d("Transfer", "Speed: ${speed / 1024} KB/s")
```

---

## 🎨 UI Customization Examples

### Voice Message Bubble Colors
```kotlin
// In ChatScreen.kt → MessageBubble

Surface(
    color = when {
        message.type == Protocol.TYPE_AUDIO_OFFER && isPlaying -> 
            MaterialTheme.colorScheme.tertiaryContainer
        message.isIncoming -> 
            MaterialTheme.colorScheme.secondaryContainer
        else -> 
            MaterialTheme.colorScheme.primaryContainer
    }
) {
    // ... message content ...
}
```

### Add Recording Animation
```kotlin
@Composable
fun RecordingPulseAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Icon(
        Icons.Default.Mic,
        contentDescription = null,
        modifier = Modifier.alpha(alpha)
    )
}
```

---

## 📱 Device Compatibility

### Tested Devices
| Device | Android Version | BLE | Classic | Status |
|--------|----------------|-----|---------|--------|
| Pixel 6 | 13 | ✅ | ✅ | Working |
| Samsung Galaxy | 12 | ✅ | ✅ | Working |
| OnePlus | 11 | ✅ | ✅ | Working |
| Xiaomi | 10 | ⚠️ | ✅ | Use Classic |
| Huawei | 9 | ❌ | ✅ | Classic Only |

### Known Limitations
- Some devices have poor BLE support → Use Classic Bluetooth
- Audio quality varies by device microphone
- Transfer speed depends on Bluetooth version (2.0 vs 5.0)
- Battery drain higher on older devices

---

## 🆘 Getting Help

### Logs
```bash
# View app logs
adb logcat | grep -E "AudioRecorder|AudioPlayer|ChatScreen"

# View Bluetooth logs
adb logcat | grep -E "GattClient|ClassicClient"

# View all app logs
adb logcat | grep "com.btmessenger"
```

### Debug Mode
```kotlin
// In AudioRecorder.kt, enable verbose logging

private val DEBUG = true

fun startRecording(): File? {
    if (DEBUG) Log.d(tag, "Starting recording...")
    // ... rest of code ...
}
```

### Report Issues
- GitHub Issues: [Create new issue]
- Include: Device model, Android version, logs
- Describe: Steps to reproduce
- Attach: Screenshots if applicable

---

## ✅ Checklist Before Release

- [ ] Test on 3+ different devices
- [ ] Test Android 9, 10, 11, 12, 13+
- [ ] Verify all permissions requested
- [ ] Check audio file cleanup
- [ ] Test with low battery
- [ ] Test with low storage
- [ ] Verify Bluetooth range
- [ ] Test rapid recording/playback
- [ ] Check memory leaks
- [ ] Verify database migrations
- [ ] Test app restart with pending messages
- [ ] Verify all strings translated
- [ ] Update version number
- [ ] Update changelog
- [ ] Create release notes

---

**You're ready to build and test voice messaging! 🎤📱** Happy coding! 🚀
