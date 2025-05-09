package com.example.chesspedagogue;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
//import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the chess coach AI functionality, including API communication,
 * text-to-speech, and managing the conversation flow.
 */
public class ChessCoachManager {
    private static final String TAG = "ChessCoachManager";

    private static ChessCoachManager instance;
    private final Context context;
    private final OpenAIService openAIService;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    // Text-to-Speech engine
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private SpeechRecognitionManager speechRecognitionManager;

    // Callback interface for responses
    public interface ChessCoachCallback {
        void onResponseReceived(String response);
        void onError(String errorMessage);
        void onSpeechCompleted();
    }

    private ChessCoachManager(Context context) {
        this.context = context.getApplicationContext();
        this.openAIService = OpenAIService.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        initTextToSpeech();
    }

    /**
     * Get the singleton instance of ChessCoachManager
     */
    public static synchronized ChessCoachManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChessCoachManager(context);
        }
        return instance;
    }

    /**
     * Set the OpenAI API key
     */
    public void setApiKey(String apiKey) {
        openAIService.setApiKey(apiKey);
    }

    // In ChessCoachManager.java, modify the initTextToSpeech method:

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported");
                } else {
                    textToSpeech.setSpeechRate(0.95f); // Slightly slower for clarity
                    textToSpeech.setPitch(1.0f);       // Normal pitch

                    // Add this line to make callbacks more reliable
                    textToSpeech.setOnUtteranceProgressListener(createUtteranceProgressListener());

                    ttsReady = true;
                    Log.d(TAG, "TTS initialization complete, callbacks registered");
                }
            } else {
                Log.e(TAG, "TTS Initialization failed with status: " + status);
            }
        });
    }

    // Add near the top of the class
    public interface VoiceRecognitionController {
        void startListening();
        boolean isInConversationMode();
    }

    private VoiceRecognitionController voiceController;

    public void setVoiceController(VoiceRecognitionController controller) {
        this.voiceController = controller;
    }

    // Add this new method
    private UtteranceProgressListener createUtteranceProgressListener() {
        return new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started speaking utterance: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS finished speaking utterance: " + utteranceId);

                // Important: Always check on main thread to avoid issues
                mainHandler.post(() -> {
                    if (currentCallback != null) {
                        Log.d(TAG, "Notifying callback of speech completion: " +
                                currentCallback.getClass().getSimpleName());
                        currentCallback.onSpeechCompleted();
                    } else {
                        Log.d(TAG, "No callback to notify for speech completion");
                    }
                });
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error with utterance: " + utteranceId);

                // Even on error, notify callback to keep conversation flowing
                mainHandler.post(() -> {
                    if (currentCallback != null) {
                        Log.d(TAG, "Notifying callback of speech error");
                        currentCallback.onSpeechCompleted();
                    }
                });
            }
        };
    }


    // Track the current callback
    private ChessCoachCallback currentCallback;

    /**
     * Get chess advice based on the current position
     */
    public void getChessAdvice(String fen, String lastMove, String playerColor, ChessCoachCallback callback) {
        this.currentCallback = callback;

        executorService.execute(() -> {
            try {
                String response = openAIService.generateChessAdvice(fen, lastMove, playerColor);
                mainHandler.post(() -> {
                    callback.onResponseReceived(response);
                    speakResponse(response);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting chess advice", e);
                mainHandler.post(() -> callback.onError("Failed to get advice: " + e.getMessage()));
            }
        });
    }

    // Add to ChessCoachManager.java
    public void analyzeAndRecordGameState(String fen, List<String> moveHistory) {
        // A simple example of recording concepts/mistakes based on position
        if (fen.contains("1k6") && fen.contains("7K")) {
            // Detect king and king endgames
            openAIService.recordConceptExplained("king and king endgames");
        }

        // Look for typical beginner mistakes in the position
        if (isKingInDanger(fen, moveHistory)) {
            openAIService.recordPlayerMistake("king safety");
        }

        if (hasUndevelopedPieces(fen) && moveHistory.size() > 10) {
            openAIService.recordPlayerMistake("incomplete development");
        }
    }

    // Example helper methods
    private boolean isKingInDanger(String fen, List<String> moveHistory) {
        // Simplified check - in a real implementation, you'd use Stockfish analysis
        return fen.contains("+ ") || fen.contains("#");
    }

    private boolean hasUndevelopedPieces(String fen) {
        // Check if knights or bishops remain on their home squares after 10+ moves
        return fen.contains("N1") || fen.contains("B1") ||
                fen.contains("n8") || fen.contains("b8");
    }

    /**
     * Get enhanced chess advice with full game context.
     * This method analyzes the current position, tracks concepts explained,
     * and maintains conversation context for better coaching.
     *
     * @param fen Current position in FEN notation
     * @param moveHistory List of moves played so far
     * @param playerColor Color the player is playing ("white" or "black")
     * @param callback Callback to receive the response
     */
    public void getEnhancedChessAdvice(String fen, List<String> moveHistory,
                                       String playerColor, ChessCoachCallback callback) {
        this.currentCallback = callback;

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Generating enhanced chess advice for position: " + fen);

                // 1. First, analyze the position and update our context tracking
                analyzeGameContext(fen, moveHistory, playerColor);

                // 2. Generate the advice with all the enhanced context
                String response = openAIService.generateEnhancedChessAdvice(fen, moveHistory, playerColor);

                // 3. Analyze the response to extract concepts that were explained
                identifyExplainedConcepts(response);

                // 4. Deliver the response on the main thread
                mainHandler.post(() -> {
                    callback.onResponseReceived(response);
                    speakResponse(response);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting enhanced chess advice", e);
                mainHandler.post(() -> callback.onError("Failed to get advice: " + e.getMessage()));
            }
        });
    }

    /**
     * Analyze the game context to enhance future responses
     */
    private void analyzeGameContext(String fen, List<String> moveHistory, String playerColor) {
        try {
            // Determine game phase
            String gamePhase = "opening";
            if (moveHistory != null) {
                int moveCount = moveHistory.size();
                if (moveCount < 10) {
                    gamePhase = "opening";
                } else if (moveCount < 30) {
                    gamePhase = "middlegame";
                } else {
                    gamePhase = "endgame";
                }
            }

            // Update context with current game state
            openAIService.updateContext("currentPosition", fen);
            openAIService.updateContext("playerColor", playerColor);
            openAIService.updateContext("gamePhase", gamePhase);

            // Perform simple position analysis to detect common patterns
            // These are very basic checks - in a full implementation you'd use
            // Stockfish analysis for more sophisticated detection

            // Check for undeveloped pieces in opening/middlegame
            if (gamePhase.equals("opening") || gamePhase.equals("middlegame")) {
                if (fen.contains("N1") || fen.contains("B1") ||
                        fen.contains("n8") || fen.contains("b8")) {
                    openAIService.recordPlayerMistake("undeveloped pieces");
                }
            }

            // Check for king safety issues
            if (fen.contains("+") || isKingExposed(fen, playerColor)) {
                openAIService.recordPlayerMistake("king safety");
            }

            // Check for pawn structure issues
            if (countIsolatedPawns(fen, playerColor) > 2) {
                openAIService.recordPlayerMistake("isolated pawns");
            }

            Log.d(TAG, "Game context analysis complete for phase: " + gamePhase);
        } catch (Exception e) {
            Log.e(TAG, "Error in analyzeGameContext", e);
            // Continue execution despite analysis errors
        }
    }

    /**
     * Identify chess concepts that were explained in the response
     */
    private void identifyExplainedConcepts(String response) {
        String lowerResponse = response.toLowerCase();

        // Check for common chess concepts in the response
        if (lowerResponse.contains("pin") && lowerResponse.contains("cannot move")) {
            openAIService.recordConceptExplained("pins");
        }

        if (lowerResponse.contains("fork") &&
                (lowerResponse.contains("attack two") || lowerResponse.contains("attacking multiple"))) {
            openAIService.recordConceptExplained("forks");
        }

        if (lowerResponse.contains("develop") && lowerResponse.contains("piece")) {
            openAIService.recordConceptExplained("development");
        }

        if (lowerResponse.contains("control") && lowerResponse.contains("center")) {
            openAIService.recordConceptExplained("center control");
        }

        if (lowerResponse.contains("castle") || lowerResponse.contains("castling")) {
            openAIService.recordConceptExplained("castling");
        }

        // Add more concept detection as needed
    }

    /**
     * Immediately stop speech and start listening again
     * @return true if speech was stopped, false if no speech was happening
     */
    public boolean interruptAndListen() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            Log.d(TAG, "Interrupting ongoing speech");
            textToSpeech.stop();

            // If we have a voice controller, start listening again
            if (voiceController != null && voiceController.isInConversationMode()) {
                voiceController.startListening();
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if king is exposed
     * This is a simplified implementation - a real one would use engine analysis
     */
    private boolean isKingExposed(String fen, String playerColor) {
        // Simple check - look for lack of pawns near king
        if (playerColor.equalsIgnoreCase("white")) {
            return fen.contains("K") && !fen.contains("KP");
        } else {
            return fen.contains("k") && !fen.contains("kp");
        }
    }

    /**
     * Helper method to count isolated pawns
     * This is a simplified implementation - a real one would use engine analysis
     */
    private int countIsolatedPawns(String fen, String playerColor) {
        // This would actually require detailed pawn structure analysis
        // For now, we return a placeholder value
        return 0;
    }
    /**
     * Send a user message to the chess coach
     */
    public void sendMessage(String message, ChessCoachCallback callback) {
        this.currentCallback = callback;

        executorService.execute(() -> {
            try {
                String response = openAIService.sendMessage(message);
                mainHandler.post(() -> {
                    callback.onResponseReceived(response);
                    speakResponse(response);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                mainHandler.post(() -> callback.onError("Failed to send message: " + e.getMessage()));
            }
        });
    }

    /**
     * Speak a response using Text-to-Speech
     */
    // In ChessCoachManager.java, modify the speakResponse method:
    private void speakResponse(String response) {
        if (ttsReady) {
            // Start background listening while speaking
            if (speechRecognitionManager != null) {
                speechRecognitionManager.startBackgroundListening(new SpeechRecognitionManager.SpeechActivityDetector() {
                    @Override
                    public void onSpeechDetected() {
                        // User is trying to speak - stop AI speech and start active listening
                        stopSpeaking();

                        // Use the voiceController instead of directly calling startVoiceRecognition
                        mainHandler.post(() -> {
                            if (voiceController != null) {
                                voiceController.startListening();
                            }
                        });
                    }
                });
            }
            // Speak the response as normal
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ChessCoach");
            textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, params, "ChessCoach");
        }
    }

    public void stopSpeaking() {
        if (ttsReady && textToSpeech.isSpeaking()) {
            textToSpeech.stop();

            // Also stop background listening
            if (speechRecognitionManager != null) {
                speechRecognitionManager.stopBackgroundListening();
            }
        }
    }


    /**
     * Reset the conversation history
     */
    public void resetConversation() {
        openAIService.resetConversation();
    }

    /**
     * Clean up resources when no longer needed
     */
    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        executorService.shutdown();
    }
}