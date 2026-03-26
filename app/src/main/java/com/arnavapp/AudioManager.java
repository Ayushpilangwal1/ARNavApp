package com.arnavapp;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class AudioManager implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private boolean isReady = false;
    private final AudioReadyListener listener;

    public interface AudioReadyListener {
        void onAudioReady();
        void onAudioError(String message);
    }

    public AudioManager(Context context, AudioReadyListener listener) {
        this.listener = listener;
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (listener != null) listener.onAudioError("TTS Language not supported");
            } else {
                isReady = true;
                if (listener != null) listener.onAudioReady();
            }
        } else {
            if (listener != null) listener.onAudioError("TTS Initialization failed");
        }
    }

    // Universal speak method
    public void speak(String text) {
        if (!isReady || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    public void announceRecordingStarted() { speak("Recording path started."); }
    public void announceTrackingStarted() { speak("Tracking ready."); }
    public void announceWaypointSaved(int count) { speak("Waypoint " + count + " saved."); }
    public void announceNavigationStarted(int total) { speak("Starting navigation to " + total + " waypoints."); }

    public void speakWaypointReached(int reachedIndex, int nextIndex) {
        if (nextIndex == -1) {
            speak("You have reached your destination.");
        } else {
            speak("Waypoint reached. Heading to next waypoint.");
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}