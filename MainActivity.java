package com.example.chesspedagogue;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
//import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
//import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Core components
    private StockfishManager engine;
    private ChessGameManager gameManager;
    private ChessBoardView boardView;
    private ChessCoachManager chessCoach;
    private SpeechRecognitionManager speechRecognitionManager;

    // Game state
    private String playerColorChoice;
    private boolean isPlayerTurn = true;
    private String lastMove = "";

    // UI components
    private TextView statusTextView;
    private CardView coachMessageCard;
    private TextView coachMessageText;
    private Button askFollowUpButton;
    private Button dismissCoachButton;
    private FloatingActionButton chessCoachButton;
    private FloatingActionButton voiceInputButton;
    private FloatingActionButton conversationButton;

    private Vibrator vibrator;
    private static final int PERMISSION_REQUEST_MICROPHONE = 101;

    // Move history tracking
    private TextView moveHistoryTextView;
    private StringBuilder moveHistoryBuilder = new StringBuilder();
    private int moveNumber = 1;
    private List<String> algebraicMoveHistory = new ArrayList<>();

    // Chat panel variables
    private View chatPanel;
    private View dragHandle;
    private float initialY;
    private float initialTouchY;
    private boolean isPanelVisible = false;
    private Animation slideUpAnimation;
    private Animation slideDownAnimation;
    private LinearLayout coachBottomSheet;
    private TextView bottomSheetMessageText;
    private Button bottomSheetRespondButton;
    private Button bottomSheetDismissButton;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    // Add these variables to your MainActivity class
    private boolean inConversationMode = false;
    private int conversationTurns = 0;
    private static final int MAX_CONVERSATION_TURNS = 5; // Prevent infinite loops

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get settings from SplashActivity
        playerColorChoice = getIntent().getStringExtra("PLAYER_COLOR");
        if (playerColorChoice == null) playerColorChoice = "white";
        int skillLevel = getIntent().getIntExtra("SKILL_LEVEL", 10);

        // Find UI components
        boardView = findViewById(R.id.chessBoardView);
        statusTextView = findViewById(R.id.statusTextView);
        moveHistoryTextView = findViewById(R.id.moveHistoryTextView);
        moveHistoryBuilder = new StringBuilder();
        moveNumber = 1;

        // Chess coach components
        coachMessageCard = findViewById(R.id.coachMessageCard);
        coachMessageText = findViewById(R.id.coachMessageText);
        askFollowUpButton = findViewById(R.id.askFollowUpButton);
        dismissCoachButton = findViewById(R.id.dismissCoachButton);
        chessCoachButton = findViewById(R.id.chessCoachButton);
        voiceInputButton = findViewById(R.id.voiceInputButton);
        conversationButton = findViewById(R.id.conversationButton);
        coachBottomSheet = findViewById(R.id.coachBottomSheet);
        bottomSheetMessageText = findViewById(R.id.bottomSheetMessageText);
        bottomSheetRespondButton = findViewById(R.id.bottomSheetRespondButton);
        bottomSheetDismissButton = findViewById(R.id.bottomSheetDismissButton);

        // Initialize the bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(coachBottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Add this in your onCreate method, after initializing the bottom sheet components
        // Make the ENTIRE bottom sheet respond to taps for interruption
        coachBottomSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Interrupt speech and start listening when user taps anywhere on the sheet
                if (chessCoach.interruptAndListen()) {
                    Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // For even better response, also make the text area specifically respond to taps
        bottomSheetMessageText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Interrupt speech and start listening when user taps the message
                if (chessCoach.interruptAndListen()) {
                    Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Add this after initializing the bottom sheet
        View bottomSheetDragHandle = findViewById(R.id.bottomSheetDragHandle);
        bottomSheetDragHandle.setOnTouchListener(new View.OnTouchListener() {
            private float initialY;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = coachBottomSheet.getY();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float currentY = event.getRawY();
                        float deltaY = currentY - initialTouchY;

                        // Convert to bottom sheet state
                        if (deltaY < -50) { // Dragging up
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        } else if (deltaY > 50) { // Dragging down
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;
                }
                return false;
            }
        });

        // Set a lower peek height that won't block the board
        int peekHeightDp = 72; // Just enough for buttons and a line of text
        int peekHeightPx = (int) (peekHeightDp * getResources().getDisplayMetrics().density);
        bottomSheetBehavior.setPeekHeight(peekHeightPx);

        // Set maximum height to prevent full coverage of board
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        int maxHeight = screenHeight / 3; // Maximum 1/3 of screen height
        bottomSheetBehavior.setMaxHeight(maxHeight);

        // Add this right after initializing coach components
        coachMessageCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Interrupt speech and start listening when user taps the message
                if (chessCoach.interruptAndListen()) {
                    Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initialize chat panel components
        chatPanel = findViewById(R.id.chatPanel);
        if (chatPanel != null) {
            dragHandle = chatPanel.findViewById(R.id.dragHandle);

            // Set up slide animations
            slideUpAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_up);
            slideDownAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_down);

            // Set up drag handle touch listener
            setupDragHandleTouchListener();

            // Set up conversation button for continuous voice interaction
            if (conversationButton != null) {
                conversationButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startVoiceConversation();
                    }
                });
            }
        }

        // Set up respond button
        bottomSheetRespondButton.setOnClickListener(v -> {
            startVoiceRecognition();
        });

        // Set up dismiss button
        bottomSheetDismissButton.setOnClickListener(v -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            chessCoach.stopSpeaking();
        });

        // Initialize the chess coach
        initializeChessCoach();

        voiceInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show a simple dialog with options
                String[] options = {"Voice command", "Type a question"};

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Chess Coach Input");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // Voice input
                            startVoiceRecognition();
                        } else {
                            // Text input - show the chat panel
                            toggleChatPanel();
                        }
                    }
                });
                builder.show();
            }
        });

        chessCoachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get current game analysis
                String fen = engine.getCurrentFEN();
                showLoading("Coach is analyzing your position...");
                chessCoach.getEnhancedChessAdvice(fen, algebraicMoveHistory,
                        playerColorChoice, new ChessCoachCallback());
            }
        });



        // Setup other coach UI elements
        askFollowUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForFollowUpQuestion();
            }
        });

        dismissCoachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCoachMessage();
            }
        });

        // In your onCreate method, after initializing other buttons
        FloatingActionButton conversationButton = findViewById(R.id.conversationButton);
        conversationButton.setOnClickListener(v -> {
            startVoiceConversation();
        });

        // Initialize sound manager
        SoundManager.initialize(this);

        // Get vibrator service
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Show initial status
        updateStatusText("Starting game...");

        // Initialize speech recognition
        initializeSpeechRecognition();

        // Load saved game after engine initialization
        loadSavedGameIfNeeded();

        // Check for and prompt for API key if needed
        promptForApiKeyIfNeeded();

        // Initialize the engine using the native library approach
        initializeStockfishEngine(skillLevel);

        // Setup game analysis button
        Button gameAnalysisButton = findViewById(R.id.gameAnalysisButton);
        if (gameAnalysisButton != null) {
            gameAnalysisButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, GameAnalysisActivity.class);
                    intent.putStringArrayListExtra("MOVE_HISTORY", new ArrayList<>(algebraicMoveHistory));
                    startActivity(intent);
                }
            });
        }
    }

    /**
     * Loads a saved game if one was requested when launching the activity.
     * This method should be called after the engine has been initialized.
     */
    private void loadSavedGameIfNeeded() {
        // Check if we're loading a saved game
        long gameId = getIntent().getLongExtra("LOAD_GAME_ID", -1);
        if (gameId != -1) {
            try {
                Log.d(TAG, "Starting to load saved game #" + gameId);

                // We're loading a saved game
                playerColorChoice = getIntent().getStringExtra("PLAYER_COLOR");
                ArrayList<String> loadedMoves = getIntent().getStringArrayListExtra("MOVE_HISTORY");
                String finalFen = getIntent().getStringExtra("FINAL_FEN");

                Log.d(TAG, "Game data received: playerColor=" + playerColorChoice +
                        ", moves=" + (loadedMoves != null ? loadedMoves.size() : 0));

                // Set up the game state from the loaded game
                if (loadedMoves != null && !loadedMoves.isEmpty()) {
                    // Store the moves in our history
                    algebraicMoveHistory = new ArrayList<>(loadedMoves);

                    // Make sure we start with a fresh game
                    engine.newGame();

                    Log.d(TAG, "Applying moves to rebuild game state...");

                    // Apply each move to rebuild the game state
                    for (String move : loadedMoves) {
                        // Make the move
                        gameManager.makeMove(move);
                        Log.d(TAG, "Applied move: " + move);
                    }

                    // Update the board display
                    updateBoardDisplay();
                    Log.d(TAG, "Board display updated");

                    // Update move history display text
                    moveHistoryBuilder = new StringBuilder();
                    moveNumber = 1;
                    for (int i = 0; i < loadedMoves.size(); i++) {
                        boolean isWhiteMove = (i % 2 == 0);
                        if (isWhiteMove) {
                            moveHistoryBuilder.append(moveNumber).append(". ").append(loadedMoves.get(i));
                        } else {
                            moveHistoryBuilder.append(" ").append(loadedMoves.get(i)).append("\n");
                            moveNumber++;
                        }
                    }
                    moveHistoryTextView.setText(moveHistoryBuilder.toString());
                    Log.d(TAG, "Move history text updated");

                    // Determine whose turn it is
                    isPlayerTurn = (loadedMoves.size() % 2 == 0 && playerColorChoice.equalsIgnoreCase("white")) ||
                            (loadedMoves.size() % 2 == 1 && playerColorChoice.equalsIgnoreCase("black"));

                    Log.d(TAG, "Turn determined: " + (isPlayerTurn ? "Player's turn" : "Engine's turn"));
                    updateStatusText("Game loaded. " + (isPlayerTurn ? "Your turn." : "Engine thinking..."));

                    // Flip the board based on player color if needed
                    boardView.setFlipped(playerColorChoice.equalsIgnoreCase("black"));

                    // If it's the engine's turn, make it move
                    if (!isPlayerTurn) {
                        makeEngineMove();
                    }

                    Toast.makeText(this, "Game loaded successfully!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Game loaded successfully!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading game: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading game: " + e.getMessage(), Toast.LENGTH_LONG).show();

                // Fall back to a new game if loading fails
                setupChessBoard();
            }
        } else {
            Log.d(TAG, "No saved game to load, setting up new game");
            // No saved game to load, so set up a new game
            setupChessBoard();
        }
    }

    /* Public interface for communication with ChessCoachManager
    public interface VoiceRecognitionController {
        void startListening();
        boolean isInConversationMode();
    }
    */

    // Implementation that can be shared with ChessCoachManager

    private ChessCoachManager.VoiceRecognitionController voiceController =
            new ChessCoachManager.VoiceRecognitionController() {
                @Override
                public void startListening() {
                    startVoiceRecognition();
                }

                @Override
                public boolean isInConversationMode() {
                    return inConversationMode;
                }
            };

    private void setupDragHandleTouchListener() {
        if (dragHandle == null) return;

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = chatPanel.getY();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float newY = initialY + (event.getRawY() - initialTouchY);
                        // Limit panel height between 150dp and screen height - 100dp
                        float minY = getWindowManager().getDefaultDisplay().getHeight() -
                                convertDpToPx(450);
                        float maxY = getWindowManager().getDefaultDisplay().getHeight() -
                                convertDpToPx(150);

                        if (newY < minY) newY = minY;
                        if (newY > maxY) newY = maxY;

                        chatPanel.setY(newY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    // In MainActivity.java, update the toggleChatPanel method:
    private void toggleChatPanel() {
        if (chatPanel == null) return;

        if (isPanelVisible) {
            // Hide panel
            chatPanel.startAnimation(slideDownAnimation);
            chatPanel.setVisibility(View.GONE);
        } else {
            // Show panel
            chatPanel.setVisibility(View.VISIBLE);
            chatPanel.startAnimation(slideUpAnimation);

            // Initialize chat components if needed
            RecyclerView recyclerView = chatPanel.findViewById(R.id.recyclerViewChat);
            if (recyclerView.getAdapter() == null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new ChatMessage(ChatMessage.TYPE_COACH,
                        "Hello! I'm Coach Tal. How can I help with your chess game?"));
                ChatAdapter adapter = new ChatAdapter(messages);
                recyclerView.setAdapter(adapter);

                // Set up message input
                EditText messageInput = chatPanel.findViewById(R.id.editTextMessage);
                ImageButton sendButton = chatPanel.findViewById(R.id.buttonSend);

                sendButton.setOnClickListener(v -> {
                    String message = messageInput.getText().toString().trim();
                    if (!message.isEmpty()) {
                        // Add user message
                        messages.add(new ChatMessage(ChatMessage.TYPE_USER, message));
                        // Create a coach response based on the current game state
                        String response = "I'm analyzing your position...";
                        messages.add(new ChatMessage(ChatMessage.TYPE_COACH, response));
                        adapter.notifyDataSetChanged();
                        recyclerView.scrollToPosition(messages.size() - 1);

                        // Process the message with your coach AI
                        chessCoach.sendMessage(message, new ChessCoachCallback());

                        // Clear input
                        messageInput.setText("");
                    }
                });
            }
        }
        isPanelVisible = !isPanelVisible;
    }

    private float convertDpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    /**
     * Start voice recognition to interact with the coach
     */

    /**
     * Initialize the Chess Coach manager
     */
    private void initializeChessCoach() {
        chessCoach = ChessCoachManager.getInstance(this);

        // Add this line to pass the controller
        chessCoach.setVoiceController(voiceController);

        // Get API key from secure storage if available
        String apiKey = ApiKeyConfig.getApiKey(this);
        if (apiKey != null && !apiKey.isEmpty()) {
            chessCoach.setApiKey(apiKey);
        }
    }

    /**
     * Initialize speech recognition
     */
    private void initializeSpeechRecognition() {
        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_MICROPHONE);
        } else {
            setupSpeechRecognition();
        }
    }

    /**
     * Set up the speech recognition manager
     */
    private void setupSpeechRecognition() {
        speechRecognitionManager = new SpeechRecognitionManager(this);
    }

    /**
     * Start a voice conversation with the chess coach
     */
    private void startVoiceConversation() {
        // Show a visual indicator that we're entering conversation mode
        Toast.makeText(this, "Starting conversation with Coach Magnus...", Toast.LENGTH_SHORT).show();

        // Reset conversation state
        inConversationMode = true;
        conversationTurns = 0;

        // Start the first recognition
        startVoiceRecognition();
    }

    /**
     * Start voice recognition specifically for conversation mode
     */
    private void startVoiceRecognitionForConversation() {
        // Visual feedback that we're listening
        showLoading("Listening...");

        // Start speech recognition
        speechRecognitionManager.startListening(new SpeechRecognitionManager.SpeechRecognitionCallback() {
            @Override
            public void onSpeechRecognized(String text) {
                if (!text.isEmpty()) {
                    // For first turn, process normally
                    if (conversationTurns == 0) {
                        processVoiceCommand(text);
                    } else {
                        // For follow-ups, process with context
                        processFollowUpCommand(text);
                    }
                    conversationTurns++;
                } else {
                    // End conversation if no speech detected
                    endConversation("I didn't catch that.");
                }
            }

            @Override
            public void onSpeechError(String error) {
                endConversation("Sorry, I had trouble hearing you.");
            }
        });
    }

    // Modify your existing startVoiceRecognition method or create this new one
    private void startVoiceRecognition() {
        // Update UI to show we're listening
        showLoading("Listening...");

        // Start speech recognition
        speechRecognitionManager.startListening(new SpeechRecognitionManager.SpeechRecognitionCallback() {
            @Override
            public void onSpeechRecognized(String text) {
                if (!text.isEmpty()) {
                    // For first turn, process normally
                    if (conversationTurns == 0) {
                        processVoiceCommand(text);
                    } else {
                        // For follow-ups, process with context
                        processFollowUpCommand(text);
                    }
                    conversationTurns++;
                } else {
                    // End conversation if no speech detected
                    endConversation("I didn't catch that.");
                }
            }

            @Override
            public void onSpeechError(String error) {
                endConversation("Sorry, I had trouble hearing you.");
            }
        });
    }


    /**
     * Process voice commands from the user, connecting them to the current chess position
     * and providing contextual responses.
     *
     * @param command The recognized speech command from the user
     */
    private void processVoiceCommand(String command) {
        Log.d(TAG, "Voice command: " + command);

        // Show what was recognized
        Toast.makeText(this, "You said: " + command, Toast.LENGTH_SHORT).show();

        // Get current position and game state for context
        String currentFen = engine.getCurrentFEN();

        // Convert command to lowercase for easier parsing
        String lowercaseCommand = command.toLowerCase();

        // Check for move commands
        if (lowercaseCommand.contains("move ")) {
            // This would need natural language parsing to convert to UCI
            // For now, we'll just pass it to the coach
            showLoading("Coach is analyzing your move request...");

            String contextualPrompt =
                    "Current board position: " + currentFen + "\n" +
                            "I'm playing as " + playerColorChoice + ".\n" +
                            "I want to: " + command;

            chessCoach.sendMessage(contextualPrompt, new ChessCoachCallback());
            return;
        }

        // Check for position advice requests
        if (lowercaseCommand.contains("what should i do") ||
                lowercaseCommand.contains("what's my best move") ||
                lowercaseCommand.contains("help me") ||
                lowercaseCommand.contains("advice") ||
                lowercaseCommand.contains("analyze") ||
                lowercaseCommand.contains("suggestion")) {

            // This is a request for position advice - use the current board state!
            showLoading("Coach is analyzing your position...");
            chessCoach.getEnhancedChessAdvice(currentFen, algebraicMoveHistory,
                    playerColorChoice, new ChessCoachCallback());
            return;
        }

        // Check for evaluation requests
        if (lowercaseCommand.contains("who's winning") ||
                lowercaseCommand.contains("who is winning") ||
                lowercaseCommand.contains("evaluation") ||
                lowercaseCommand.contains("am i winning") ||
                lowercaseCommand.contains("score")) {

            showLoading("Coach is evaluating the position...");
            String contextualPrompt =
                    "Current board position: " + currentFen + "\n" +
                            "I'm playing as " + playerColorChoice + ".\n" +
                            "Please evaluate who's winning in this position and by approximately how much.";

            chessCoach.sendMessage(contextualPrompt, new ChessCoachCallback());
            return;
        }

        // Default: treat as a general question, but INCLUDE the board context
        showLoading("Coach is considering your question...");

        // Create a context-aware prompt that includes the current board state
        String contextualPrompt =
                "Current board position: " + currentFen + "\n" +
                        "I'm playing as " + playerColorChoice + ".\n" +
                        "My question is: " + command;

        chessCoach.sendMessage(contextualPrompt, new ChessCoachCallback());
    }


    /**
     * Process a follow-up command in an ongoing conversation
     */
    private void processFollowUpCommand(String command) {
        Log.d(TAG, "Follow-up command: " + command);

        // Show what was recognized
        Toast.makeText(this, "You said: " + command, Toast.LENGTH_SHORT).show();

        // Create a context-aware prompt for the follow-up
        String contextualPrompt =
                "This is a follow-up question in our conversation.\n\n" +
                        "Current board position: " + engine.getCurrentFEN() + "\n" +
                        "I'm playing as " + playerColorChoice + ".\n" +
                        "My follow-up question is: " + command;

        // Send to the coach with our special callback that continues the conversation
        showLoading("Coach is thinking...");
        chessCoach.sendMessage(contextualPrompt, new ConversationContinuingCallback());
    }

    /**
     * End the conversation gracefully
     */
    private void endConversation(String message) {
        inConversationMode = false;
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
        // Reset any conversation-specific UI elements
    }

    /**
     * Special callback that continues the conversation after the coach responds
     */
    private class ConversationContinuingCallback implements ChessCoachManager.ChessCoachCallback {
        @Override
        public void onResponseReceived(String response) {
            // Show the coach's response
            showCoachMessage(response);
        }

        @Override
        public void onError(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            endConversation(null);
        }

        @Override
        public void onSpeechCompleted() {
            // This is the magic moment - when the coach finishes speaking,
            // we start listening again to continue the conversation!
            Log.d(TAG, "ConversationContinuingCallback.onSpeechCompleted called. inConversationMode="
                    + inConversationMode + ", conversationTurns=" + conversationTurns);
            if (inConversationMode && conversationTurns < MAX_CONVERSATION_TURNS) {
                // Add a small delay so the user has time to think
                new Handler().postDelayed(() -> {
                    runOnUiThread(() -> {
                        // Start listening again
                        startVoiceRecognition();
                    });
                }, 1500); // 1.5 second pause
            } else {
                // We've reached our turn limit, end gracefully
                endConversation("Thanks for the conversation!");
            }
        }
    }

    /**
     * Show dialog for interacting with the chess coach
     */
    /**
     * Show dialog for interacting with the chess coach
     */
    private void showCoachDialog() {
        String[] options = {
                "Get advice on my position",
                "Ask about my last move",
                "General chess question",
                "Cancel"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chess Coach");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Get advice
                    String fen = engine.getCurrentFEN();
                    showLoading("Coach is analyzing your game...");
                    // Use the enhanced method with move history
                    chessCoach.getEnhancedChessAdvice(fen, algebraicMoveHistory,
                            playerColorChoice, new ChessCoachCallback());
                    break;

                case 1: // Ask about last move
                    if (lastMove.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No moves played yet", Toast.LENGTH_SHORT).show();
                    } else {
                        showLoading("Coach is analyzing your move...");
                        // We can also send move history here for better context
                        String currentFen = engine.getCurrentFEN();
                        String moveQuestion = "Was my last move " + convertToAlgebraic(lastMove) + " good? Why or why not?";

                        // Create context string with board state and move history
                        StringBuilder context = new StringBuilder(moveQuestion);
                        context.append("\n\nCurrent position (FEN): ").append(currentFen);

                        if (!algebraicMoveHistory.isEmpty()) {
                            context.append("\n\nGame moves so far:\n");
                            int moveNum = 1;
                            for (int i = 0; i < algebraicMoveHistory.size(); i += 2) {
                                context.append(moveNum).append(". ");
                                context.append(algebraicMoveHistory.get(i));
                                if (i + 1 < algebraicMoveHistory.size()) {
                                    context.append(" ").append(algebraicMoveHistory.get(i + 1));
                                }
                                context.append("\n");
                                moveNum++;
                            }
                        }

                        chessCoach.sendMessage(context.toString(), new ChessCoachCallback());
                    }
                    break;

                case 2: // General question
                    promptForGeneralQuestion();
                    break;

                case 3: // Cancel
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }

    /**
     * Prompt for a general chess question
     */
    private void promptForGeneralQuestion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ask the Coach");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("What would you like to ask?");
        builder.setView(input);

        builder.setPositiveButton("Ask", (dialog, which) -> {
            String question = input.getText().toString().trim();
            if (!question.isEmpty()) {
                showLoading("Coach is thinking...");
                chessCoach.sendMessage(question, new ChessCoachCallback());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Prompt for a follow-up question
     */
    private void promptForFollowUpQuestion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow-up Question");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Ask your follow-up question");
        builder.setView(input);

        builder.setPositiveButton("Ask", (dialog, which) -> {
            String question = input.getText().toString().trim();
            if (!question.isEmpty()) {
                showLoading("Coach is thinking...");
                chessCoach.sendMessage(question, new ChessCoachCallback());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Show the loading indicator while waiting for the coach
     */
    private void showLoading(String message) {
        // Update the bottom sheet with the loading message
        bottomSheetMessageText.setText(message);

        // Show the bottom sheet in collapsed state
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Hide the respond button while loading
        if (bottomSheetRespondButton != null) {
            bottomSheetRespondButton.setVisibility(View.GONE);
        }

        // DON'T use the old message card anymore
        // coachMessageCard.setVisibility(View.VISIBLE); - REMOVE this line
        // coachMessageText.setText(message); - REMOVE this line
        // askFollowUpButton.setVisibility(View.GONE); - REMOVE this line
    }
    private void showScrollingMessage(String message) {
        // Set message text
        bottomSheetMessageText.setText(message);

        // Reset scroll position
        ScrollView scrollView = (ScrollView) bottomSheetMessageText.getParent();
        scrollView.scrollTo(0, 0);

        // Start scrolling animation after a small delay
        scrollView.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Calculate scroll speed based on text length
                int duration = Math.min(message.length() * 30, 12000); // Max 12 seconds

                // Create smooth scroll animation
                ObjectAnimator animator = ObjectAnimator.ofInt(
                        scrollView, "scrollY",
                        0, bottomSheetMessageText.getHeight());
                animator.setDuration(duration);
                animator.setInterpolator(new LinearInterpolator());
                animator.start();
            }
        }, 2000); // 2 second delay before starting scroll
    }

    /**
     * Display the coach's message
     */
    private void showCoachMessage(String message) {
        // ONLY update the bottom sheet, not the popup card
        bottomSheetMessageText.setText(message);

        // Show the bottom sheet
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // DON'T do anything with the old coachMessageCard
        // coachMessageCard.setVisibility(View.VISIBLE); - REMOVE this line
        // coachMessageText.setText(message); - REMOVE this line

        // Make sure the follow-up button in the BOTTOM SHEET is visible
        if (bottomSheetRespondButton != null) {
            bottomSheetRespondButton.setVisibility(View.VISIBLE);
        }
    }
    /**
     * Hide the coach message card
     */
    private void hideCoachMessage() {
        // Hide the bottom sheet
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Don't worry about the old card anymore
        // coachMessageCard.setVisibility(View.GONE); - REMOVE this line

        // Stop speaking
        chessCoach.stopSpeaking();
    }
    /**
     * Callback for chess coach responses
     */
    private class ChessCoachCallback implements ChessCoachManager.ChessCoachCallback {
        @Override
        public void onResponseReceived(String response) {
            showCoachMessage(response);
        }

        @Override
        public void onError(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            hideCoachMessage();
        }

        @Override
        public void onSpeechCompleted() {
            // Speech has completed, can perform any needed actions here
        }
    }

    /**
     * Prompt for API key if not already configured
     */
    private void promptForApiKeyIfNeeded() {
        if (!ApiKeyConfig.hasApiKey(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("OpenAI API Key Required");
            builder.setMessage("To use the chess coach feature, you need to enter your OpenAI API key.");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint("Enter your OpenAI API key");
            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String apiKey = input.getText().toString().trim();
                if (!apiKey.isEmpty()) {
                    ApiKeyConfig.saveApiKey(this, apiKey);
                    chessCoach.setApiKey(apiKey);
                    Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.cancel();
                Toast.makeText(this, "Chess coach features will be unavailable", Toast.LENGTH_LONG).show();
            });

            builder.show();
        } else {
            // Use the saved API key
            String apiKey = ApiKeyConfig.getApiKey(this);
            if (apiKey != null && !apiKey.isEmpty()) {
                chessCoach.setApiKey(apiKey);
            }
        }
    }

    /**
     * Initialize the Stockfish chess engine
     */
    private void initializeStockfishEngine(int skillLevel) {
        try {
            // Try using the native library first
            File engineFile = new File(getApplicationInfo().nativeLibraryDir, "libstockfish.so");

            // If not found, try extracting from assets as fallback
            if (!engineFile.exists()) {
                Log.d(TAG, "Stockfish not found in native library dir, trying assets fallback");
                try {
                    engineFile = Utils.copyAssetToExecutableDir(this, "stockfish", "stockfish");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to extract Stockfish from assets", e);
                }
            }

            if (!engineFile.exists() || !engineFile.canExecute()) {
                Toast.makeText(this, "Stockfish engine not found", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "Found Stockfish at: " + engineFile.getAbsolutePath() +
                    ", can execute: " + engineFile.canExecute());

            // Initialize the engine
            engine = new StockfishManager();
            if (engine.startEngine(engineFile.getAbsolutePath())) {
                // Configure the engine
                engine.setSkillLevel(skillLevel);
                engine.newGame();

                // Initialize game manager
                gameManager = new ChessGameManager(engine);

                // Set up the board based on player color
                setupChessBoard();

                Toast.makeText(this, "Stockfish engine initialized successfully",
                        Toast.LENGTH_SHORT).show();

                updateStatusText("Game ready! " +
                        (playerColorChoice.equalsIgnoreCase("white") ? "White" : "Black") +
                        " to move");
            } else {
                Toast.makeText(this, "Failed to start Stockfish engine",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Stockfish", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // Handle permission request results
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_MICROPHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupSpeechRecognition();
            } else {
                Toast.makeText(this,
                        "Microphone permission is required for voice commands",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Set up the chess board and move handling
     */
    private void setupChessBoard() {
        // Flip the board if the player is playing as black
        if (playerColorChoice.equalsIgnoreCase("black")) {
            boardView.setFlipped(true);
            isPlayerTurn = false;

            // If player is black, let engine (white) make the first move
            makeEngineMove();
        } else {
            boardView.setFlipped(false);
            isPlayerTurn = true;
        }

        // Set up board click listener with proper selection handling
        boardView.setOnSquareTapListener(new ChessBoardView.OnSquareTapListener() {
            @Override
            public void onSquareTapped(int row, int col) {
                handleBoardTap(row, col);
            }
        });
    }

    // Create a helper method for haptic feedback
    private void performHapticFeedback(String moveType) {
        if (vibrator != null && vibrator.hasVibrator()) {
            switch (moveType) {
                case "move":
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
                    break;
                case "capture":
                    // Stronger vibration for captures
                    vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                    break;
                case "check":
                    // Pattern for check - two quick pulses
                    long[] pattern = {0, 30, 80, 30};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    break;
            }
        }
    }

    // Add this helper method to determine move type
    private String determineMoveType(String moveUci) {
        // Convert coordinates
        int fromCol = moveUci.charAt(0) - 'a';
        int fromRow = 8 - Character.getNumericValue(moveUci.charAt(1));
        int toCol = moveUci.charAt(2) - 'a';
        int toRow = 8 - Character.getNumericValue(moveUci.charAt(3));

        // Check if it's a capture
        char pieceAtDestination = boardView.getPieceAt(toRow, toCol);
        if (pieceAtDestination != ' ') {
            return "capture";
        }

        // We would need to check if this move puts opponent in check
        // This requires more game state knowledge, but for now we can simplify
        return "move";
    }

    /**
     * Show legal moves for the selected piece
     */
    private void showLegalMovesFor(int row, int col) {
        // Clear previous highlights
        boardView.clearHighlightedSquares();

        // Get legal moves from the engine
        List<String> legalMoves = engine.getLegalMovesForPiece(row, col);

        // Add highlight for each legal move
        for (String move : legalMoves) {
            if (move.length() >= 4) {
                // Convert the target square coordinates
                int targetCol = move.charAt(2) - 'a';
                int targetRow = 8 - (move.charAt(3) - '0');
                boardView.addHighlightedSquare(targetRow, targetCol);
            }
        }

        // Refresh the board view
        boardView.invalidate();
    }

    private void handleBoardTap(int row, int col) {
        Log.d(TAG, "Tap received at: " + row + "," + col);

        // Get the currently selected square, if any
        int selectedRow = boardView.getSelectedRow();
        int selectedCol = boardView.getSelectedCol();

        // Get the piece at the tapped square
        char tappedPiece = boardView.getPieceAt(row, col);
        boolean isTappingOwnPiece = isPlayerPiece(tappedPiece);

        Log.d(TAG, "Selected: " + selectedRow + "," + selectedCol +
                " Tapped: " + row + "," + col +
                " Own piece: " + isTappingOwnPiece);

        // Case 1: Tapping the already selected piece - deselect it
        if (selectedRow == row && selectedCol == col) {
            Log.d(TAG, "Deselecting piece");
            boardView.clearSelectionHighlight();
            boardView.clearHighlightedSquares();
            return;
        }

        // Case 2: No piece selected yet, and tapping own piece
        if (selectedRow == -1 && isTappingOwnPiece && isPlayerTurn) {
            Log.d(TAG, "Selecting new piece");
            boardView.setSelectedSquare(row, col);
            showLegalMovesFor(row, col);
            return;
        }

        // Case 3: A piece is already selected
        if (selectedRow != -1) {
            // Case 3a: Tapping own piece - change selection
            if (isTappingOwnPiece && isPlayerTurn) {
                Log.d(TAG, "Changing selection to new piece");
                boardView.clearSelectionHighlight();
                boardView.clearHighlightedSquares();
                boardView.setSelectedSquare(row, col);
                showLegalMovesFor(row, col);
                return;
            }

            // Case 3b: Tapping destination square - attempt move
            String moveUci = convertToUCI(selectedRow, selectedCol, row, col);
            Log.d(TAG, "Attempting move: " + moveUci);

            if (engine.isLegalMove(moveUci)) {
                Log.d(TAG, "Move is legal, executing");
                executeMoveAndRespond(moveUci);
            } else {
                Log.d(TAG, "Move is illegal, ignoring");
                // Keep the current selection
            }
        }
    }

    /**
     * Check if a piece belongs to the player
     */
    private boolean isPlayerPiece(char piece) {
        if (piece == ' ') return false;

        boolean isPieceWhite = Character.isUpperCase(piece);
        boolean isPlayerWhite = playerColorChoice.equalsIgnoreCase("white");

        return isPieceWhite == isPlayerWhite;
    }

    /**
     * Execute a player move and get the engine's response
     */
    private void executeMoveAndRespond(String moveUci) {
        // Clear highlights
        boardView.clearSelectionHighlight();
        boardView.clearHighlightedSquares();

        // Determine move type for appropriate feedback
        String moveType = determineMoveType(moveUci);

        // Add move to history in algebraic notation
        String algebraicMove = convertToAlgebraic(moveUci);
        algebraicMoveHistory.add(algebraicMove);

        // Provide feedback based on move type
        SoundManager.playSound(moveType);
        performHapticFeedback(moveType);

        // Make the player's move
        gameManager.makeMove(moveUci);
        // ► grab coords before board refresh
        int fCol = moveUci.charAt(0)-'a', fRow = 8-(moveUci.charAt(1)-'0');
        int tCol = moveUci.charAt(2)-'a', tRow = 8-(moveUci.charAt(3)-'0');

        updateBoardDisplay();              // refresh pieces on their new squares
        boardView.animateMove(fRow,fCol,tRow,tCol);   // ► slide the piece

        // Update game state
        isPlayerTurn = false;
        lastMove = moveUci;

        // Update move history with the player's move
        boolean isWhiteMove = playerColorChoice.equalsIgnoreCase("white");
        updateMoveHistory(convertToAlgebraic(moveUci), isWhiteMove);

        updateStatusText("You moved " + convertToAlgebraic(moveUci) + ". Engine thinking...");

        // Let the engine respond
        makeEngineMove();
    }

    /**
     * Have the engine make a move
     */
    private void makeEngineMove() {
        // Have the engine make its move
        String engineMove = engine.getBestMove(1000);

        if (engineMove != null && !engineMove.isEmpty()) {
            gameManager.makeMove(engineMove);
            // ► coords for animation
            int fCol = engineMove.charAt(0)-'a', fRow = 8-(engineMove.charAt(1)-'0');
            int tCol = engineMove.charAt(2)-'a', tRow = 8-(engineMove.charAt(3)-'0');

            updateBoardDisplay();
            boardView.animateMove(fRow,fCol,tRow,tCol);   // ► animate engine move

            // Update move history with the engine's move
            boolean isWhiteMove = !playerColorChoice.equalsIgnoreCase("white");
            updateMoveHistory(convertToAlgebraic(engineMove), isWhiteMove);

            // Add engine's move to history
            String algebraicMove = convertToAlgebraic(engineMove);
            algebraicMoveHistory.add(algebraicMove);

            // Update game state
            isPlayerTurn = true;
            lastMove = engineMove;
            updateStatusText("Engine moved " + convertToAlgebraic(engineMove) + ". Your turn.");

            // Check game status (checkmate, stalemate, etc.)
            checkGameStatus();
        } else {
            updateStatusText("Engine couldn't find a move. Game may be over.");
        }
    }

    /**
     * Update the chess board display
     */
    private void updateBoardDisplay() {
        boardView.updateBoardFromFen(engine.getCurrentFEN());
    }

    /**
     * Convert board coordinates to UCI format
     */
    private String convertToUCI(int fromRow, int fromCol, int toRow, int toCol) {
        // Convert board coordinates to UCI format (e.g., "e2e4")
        char fromFile = (char) ('a' + fromCol);
        int fromRank = 8 - fromRow;
        char toFile = (char) ('a' + toCol);
        int toRank = 8 - toRow;
        return "" + fromFile + fromRank + toFile + toRank;
    }

    /**
     * Convert UCI format to algebraic notation (improved)
     */
    private String convertToAlgebraic(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) return "?";

        // Get the piece at the start square
        int fromCol = uciMove.charAt(0) - 'a';
        int fromRow = 8 - Character.getNumericValue(uciMove.charAt(1));
        char piece = boardView.getPieceAt(fromRow, fromCol);

        // Get letter for the piece (uppercase)
        String pieceLetter = "";
        switch (Character.toUpperCase(piece)) {
            case 'R': pieceLetter = "R"; break;
            case 'N': pieceLetter = "N"; break;
            case 'B': pieceLetter = "B"; break;
            case 'Q': pieceLetter = "Q"; break;
            case 'K': pieceLetter = "K"; break;
            // No letter for pawns
        }

        // Get the destination square
        String destSquare = uciMove.substring(2, 4);

        return pieceLetter + destSquare;
    }

    /**
     * Check if the game has ended (checkmate, stalemate, etc.)
     */
    private void checkGameStatus() {
        // Get and parse the FEN to check for check/checkmate
        String fen = engine.getCurrentFEN();
        if (fen == null) return;

        // This is where you'd add logic to detect checkmate, etc.
        // For now, we'll leave this as a placeholder
    }

    /**
     * Update the move history TextView
     */
    private void updateMoveHistory(String move, boolean isWhiteMove) {
        Log.d(TAG, "Updating move history: " + move + ", White move: " + isWhiteMove);

        if (moveHistoryTextView == null) {
            moveHistoryTextView = findViewById(R.id.moveHistoryTextView);
            if (moveHistoryTextView == null) {
                Log.e(TAG, "Could not find moveHistoryTextView!");
                return;
            }
        }

        if (isWhiteMove) {
            // Start a new move pair
            moveHistoryBuilder.append(moveNumber).append(". ").append(move);
        } else {
            // Complete the move pair and increment move number
            moveHistoryBuilder.append(" ").append(move).append("\n");
            moveNumber++;
        }

        final String historyText = moveHistoryBuilder.toString();
        Log.d(TAG, "Setting move history text: " + historyText);

        // Update on UI thread
        runOnUiThread(() -> {
            if (moveHistoryTextView != null) {
                moveHistoryTextView.setText(historyText);
            }
        });
    }

    // Add this method to MainActivity.java
    private void startChessConversation() {
        Intent intent = new Intent(this, ChessConversationActivity.class);
        intent.putExtra("FEN", engine.getCurrentFEN());
        intent.putStringArrayListExtra("MOVE_HISTORY", new ArrayList<>(algebraicMoveHistory));
        intent.putExtra("PLAYER_COLOR", playerColorChoice);
        startActivity(intent);
    }

    private void updateMainMenuOptions() {
        // Find your menu button or create a new one
    }

    /**
     * Update the status text view
     */
    private void updateStatusText(String message) {
        if (statusTextView != null) {
            statusTextView.setText(message);
        }
    }

    // Add this method to MainActivity.java
    private void showSaveGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Game");

        // Add an edit text for the game description
        final EditText input = new EditText(this);
        input.setHint("Enter a description for this game");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String description = input.getText().toString().trim();
                if (description.isEmpty()) {
                    description = "Game on " + new Date().toString();
                }

                // Save the game
                GameDatabaseHelper dbHelper = new GameDatabaseHelper(MainActivity.this);
                dbHelper.saveGame(
                        playerColorChoice,
                        algebraicMoveHistory,
                        engine.getCurrentFEN(),
                        description
                );

                Toast.makeText(MainActivity.this, "Game saved successfully!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    // Also add this method to view saved games
    private void openSavedGamesScreen() {
        Intent intent = new Intent(this, SavedGamesActivity.class);
        startActivity(intent);
    }

    // In MainActivity.java, add:
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save_game) {
            showSaveGameDialog();
            return true;
        }
        else if (id == R.id.action_load_game) {
            openSavedGamesScreen();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) {
            engine.stopEngine();
        }
        SoundManager.release();

        // Clean up chess coach resources
        if (chessCoach != null) {
            chessCoach.shutdown();
        }

        // Clean up speech recognition resources
        if (speechRecognitionManager != null) {
            speechRecognitionManager.release();
        }
    }
}