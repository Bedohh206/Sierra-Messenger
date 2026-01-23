# Voice Messaging Feature Guide

## Overview

The Bluetooth Messenger app now includes **voice messaging** capability, designed specifically for communities with poor network connectivity and users with varying literacy levels. Voice messages allow natural communication without requiring typing skills.

## Why Voice Messages?

### Benefits for Developing Communities:
- **Low Literacy Support**: Communicate without reading or writing
- **Natural Communication**: Express emotions and tone of voice
- **Faster**: Speaking is quicker than typing for many users
- **Inclusive**: Accessible to elderly and users with disabilities
- **No Network Required**: Works completely offline via Bluetooth

### Technical Benefits:
- **Compact**: 3GP format with AMR-NB codec (efficient compression)
- **Short Duration**: Optimized for quick messages
- **Low Bandwidth**: Suitable for Bluetooth transfer speeds
- **Local Storage**: Messages stored on device for replay

## How to Use Voice Messages

### Recording a Voice Message

1. **Open Chat**: Connect to a nearby device and open the chat screen
2. **Check Permissions**: Ensure microphone permission is granted
3. **Start Recording**: Tap and hold the microphone icon 🎤
4. **Record**: Speak your message (minimum 1 second)
5. **Send**: Release or tap the send button to send
6. **Cancel**: Tap the X button to discard the recording

### Playback Controls

- **Play**: Tap the play button ▶️ on any voice message
- **Pause**: Tap the pause button ⏸️ during playback
- **Duration**: See the length of each voice message
- **Visual Feedback**: Playing messages are highlighted

## Technical Specifications

### Audio Format
- **Codec**: AMR-NB (Adaptive Multi-Rate Narrowband)
- **Format**: 3GP container
- **Sample Rate**: 8 kHz
- **Bit Rate**: ~12.2 kbps
- **Quality**: Optimized for voice (not music)

### File Sizes (Approximate)
- 5 seconds: ~7.5 KB
- 10 seconds: ~15 KB
- 30 seconds: ~45 KB
- 1 minute: ~90 KB

### Protocol Messages

#### Audio Offer
```json
{
  "v": 1,
  "type": "AUDIO_OFFER",
  "msgId": "unique-id",
  "from": "device-a",
  "to": "device-b",
  "ts": 1704700000000,
  "fileName": "voice_1704700000000.3gp",
  "fileSize": 15420,
  "mime": "audio/3gpp",
  "duration": 5
}
```

#### Audio Accept
```json
{
  "v": 1,
  "type": "AUDIO_ACCEPT",
  "msgId": "new-id",
  "from": "device-b",
  "to": "device-a",
  "ts": 1704700001000,
  "body": "original-audio-offer-msgId"
}
```

## User Interface

### Recording Indicator
When recording, the bottom bar shows:
- **Red background**: Visual recording indicator
- **Microphone icon**: Shows active recording
- **Timer**: Real-time duration display (0:05, 0:10, etc.)
- **Cancel button**: Discard recording
- **Send button**: Send the voice message (enabled after 1 second)

### Message Bubble
Voice messages appear as:
- **Play/Pause icon**: Large, easy to tap
- **"Voice Message" label**: Clear identification
- **Duration**: Shows message length (0:05, 1:23, etc.)
- **Timestamp**: When message was sent/received
- **Color coding**: Blue for sent, grey for received

### Accessibility Features
- **Large touch targets**: Easy to tap for all users
- **Visual feedback**: Clear indication of recording state
- **Duration display**: Know length before and during playback
- **Simple controls**: One-tap play/pause

## Best Practices

### For Users
- **Keep it short**: 5-30 seconds is ideal
- **Find quiet space**: Reduce background noise
- **Hold device close**: Ensure clear audio
- **Listen first**: Review before sending if needed
- **Respect privacy**: Only send in appropriate contexts

### For Developers
- **Error handling**: Graceful fallbacks for permission issues
- **Storage management**: Clean up old audio files periodically
- **Battery consideration**: Optimize recording settings
- **Compression**: Use efficient codecs (AMR-NB)
- **User feedback**: Clear visual/audio indicators

## Implementation Details

### AudioRecorder.kt
- Uses Android MediaRecorder API
- Records to internal storage (`files/audio/`)
- Validates minimum duration (1 second)
- Automatic cleanup on cancel
- State flow for UI updates

### AudioPlayer.kt
- Uses Android MediaPlayer API
- Supports play/pause/resume
- Automatic completion callback
- Position tracking for progress bars
- Resource cleanup on stop

### ChatScreen.kt
- Integrated recording UI
- Permission handling
- Real-time duration updates
- Playback controls in message bubbles
- Bluetooth transfer integration

## Troubleshooting

### Recording Issues

**Problem**: "Can't start recording"
- **Solution**: Check microphone permission in settings
- Grant RECORD_AUDIO permission

**Problem**: "Recording too short"
- **Solution**: Hold for at least 1 second before releasing

**Problem**: "No sound recorded"
- **Solution**: Check device microphone, try restarting app

### Playback Issues

**Problem**: "Can't play voice message"
- **Solution**: File may be corrupted, ask sender to resend

**Problem**: "Playback stops unexpectedly"
- **Solution**: Check device audio settings, ensure not muted

### Transfer Issues

**Problem**: "Voice message not sending"
- **Solution**: Check Bluetooth connection, file may be too large

**Problem**: "Received voice message missing"
- **Solution**: Ensure READ_MEDIA_AUDIO permission granted

## Privacy & Security

### Current Implementation
- **Local Storage**: All audio files stored locally on device
- **No Cloud**: No data sent to external servers
- **Peer-to-Peer**: Direct Bluetooth transfer only
- **Manual Delete**: Users can delete conversations

### Future Enhancements
- **Encryption**: Optional end-to-end encryption
- **Auto-delete**: Configurable message expiration
- **Playback control**: Speed adjustment (0.5x to 2x)
- **Noise reduction**: Audio preprocessing for clarity

## Community Impact

### Target Users
- Rural communities with limited network coverage
- Elderly users uncomfortable with typing
- Users with visual impairments
- Low-literacy populations
- Emergency communication scenarios
- Market vendors and traders
- Community organizers

### Use Cases
- **Market Communication**: Quick price updates between vendors
- **Family**: Stay connected with elderly relatives
- **Emergency**: Rapid information sharing during disasters
- **Education**: Audio assignments in low-resource schools
- **Healthcare**: Patient-doctor communication in clinics
- **Agriculture**: Farmer-to-farmer advice sharing

## Performance Optimization

### Battery Life
- Efficient AMR-NB codec (~12 kbps)
- Stop recording when app backgrounded
- Release resources immediately after use
- No continuous microphone monitoring

### Storage Management
- Compressed 3GP format (small file sizes)
- Store only received/sent messages
- Clean up on message delete
- Configurable storage limits (future)

### Bluetooth Transfer
- Audio files typically < 100 KB
- Fast transfer over Bluetooth (~1-2 seconds)
- Chunked transfer for reliability (future)
- Resume on connection loss (future)

## Accessibility Compliance

- **WCAG 2.1 Level AA** considerations
- Large touch targets (48x48 dp minimum)
- High contrast visual indicators
- Clear audio feedback
- Screen reader compatible (future)
- Alternative text input always available

## Testing Recommendations

1. **Different Devices**: Test on various Android versions
2. **Noise Environments**: Test in quiet and noisy settings
3. **Duration Tests**: Record 1s, 30s, and 60s messages
4. **Battery Impact**: Monitor battery drain during use
5. **Bluetooth Range**: Test at different distances
6. **Simultaneous Use**: Multiple users in same area
7. **Low Storage**: Test when device storage is nearly full

## Support & Resources

- **Documentation**: See main README.md
- **Issues**: Report bugs via GitHub issues
- **Contributions**: Pull requests welcome
- **Community**: Discussion forum for users

---

**Voice messaging empowers communities to communicate naturally, regardless of literacy level or network availability!** 🎤📱
