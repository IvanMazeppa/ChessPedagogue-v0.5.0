package com.example.chesspedagogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Manages speech recognition functionality
 */
public class SpeechRecognitionManager {
    private static final String TAG = "SpeechRecognition";

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean backgroundListeningActive = false;

    /**
     * Callback interface for speech recognition results
     */
    public interface SpeechRecognitionCallback {
        void onSpeechRecognized(String text);
        void onSpeechError(String error);
    }

    public SpeechRecognitionManager(Context context) {
        this.context = context;
        initializeSpeechRecognizer();
    }

    /**
     * Initialize the speech recognizer
     */
    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } else {
            Log.e(TAG, "Speech recognition not available on this device");
        }
    }

    /**
     * Start listening for speech
     */
    public void startListening(SpeechRecognitionCallback callback) {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer is not available");
            callback.onSpeechError("Speech recognition not available on this device");
            return;
        }

        if (isListening) {
            stopListening();
        }

        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    Log.d(TAG, "Ready for speech");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Sound level changed
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Buffer received
                }

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    Log.d(TAG, "End of speech");
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    String errorMessage = getErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    callback.onSpeechError(errorMessage);
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        Log.d(TAG, "Speech recognized: " + text);
                        callback.onSpeechRecognized(text);
                    } else {
                        callback.onSpeechError("No speech detected");
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Partial results available
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Event occurred
                }
            });

            speechRecognizer.startListening(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            callback.onSpeechError("Error: " + e.getMessage());
        }
    }

    // Add this new method to SpeechRecognitionManager.java
    public void startBackgroundListening(final SpeechActivityDetector callback) {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer is not available for background listening");
            return;
        }

        try {
            backgroundListeningActive = true;

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
            // Key difference: We want partial results for background listening
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Background listening ready");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Background speech detected - may be an interruption");
                    // This is often the first sign that the user is speaking
                    if (backgroundListeningActive) {
                        // If we get here, user is definitely speaking - good time to trigger
                        backgroundListeningActive = false;
                        callback.onSpeechDetected();
                    }
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // For a very responsive system, we could use sound level to detect speech
                    // A sudden increase in RMS (volume) often indicates speech starting
                    if (backgroundListeningActive && rmsdB > 4.0) { // Threshold for speech
                        Log.d(TAG, "Volume spike detected - potential interruption");
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Not used for our purpose
                }

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Background speech ended");
                }

                @Override
                public void onError(int error) {
                    // Most errors in background mode are expected (no speech, etc.)
                    // Just restart listening if it was network or audio related
                    if (backgroundListeningActive) {
                        if (error == SpeechRecognizer.ERROR_NETWORK ||
                                error == SpeechRecognizer.ERROR_AUDIO ||
                                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {

                            // These errors may mean we need to restart
                            stopBackgroundListening();
                            if (backgroundListeningActive) {
                                // Try to restart if we're still supposed to be listening
                                startBackgroundListening(callback);
                            }
                        }
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    if (backgroundListeningActive) {
                        // If we get full results, user definitely said something substantial
                        backgroundListeningActive = false;
                        callback.onSpeechDetected();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (!backgroundListeningActive) return;

                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                    // If we have partial results with reasonable content, consider it an interruption
                    if (matches != null && !matches.isEmpty() && !matches.get(0).trim().isEmpty()) {
                        Log.d(TAG, "Background partial result detected: " + matches.get(0));
                        backgroundListeningActive = false;
                        callback.onSpeechDetected();
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Not used for our purpose
                }
            });

            speechRecognizer.startListening(intent);
            Log.d(TAG, "Background listening started");

        } catch (Exception e) {
            Log.e(TAG, "Error starting background speech recognition", e);
            backgroundListeningActive = false;
        }
    }

    // Add method to stop background listening
    public void stopBackgroundListening() {
        backgroundListeningActive = false;
        if (speechRecognizer != null) {
            speechRecognizer.cancel(); // Use cancel instead of stopListening for cleaner switch
        }
        Log.d(TAG, "Background listening stopped");
    }

    // Add interface for speech detection
    public interface SpeechActivityDetector {
        void onSpeechDetected();
    }

    /**
     * Stop listening for speech
     */
    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    /**
     * Convert error code to readable message
     */
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No matching speech";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected";
            default:
                return "Unknown error";
        }
    }

    /**
     * Check if currently listening
     */
    public boolean isListening() {
        return isListening;
    }

    /**
     * Release resources
     */
    public void release() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
    }
}