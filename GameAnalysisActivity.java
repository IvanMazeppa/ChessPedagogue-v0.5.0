package com.example.chesspedagogue;

import android.annotation.SuppressLint;
//import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
//import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for analyzing chess games move by move with Stockfish engine.
 */
public class GameAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "GameAnalysisActivity";

    // Core components
    private StockfishManager engine;
    private ChessBoardView boardView;

    // Game data
    private ArrayList<String> moveHistory;
    private int currentMoveIndex = -1; // -1 means initial position
    private String[] positions; // FEN positions for each move

    // UI components
    private TextView moveInfoTextView;
    private TextView analysisTextView;
    private Button prevButton;
    private Button nextButton;
    private Button analyzeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_analysis);

        // Find UI components
        boardView = findViewById(R.id.analysisBoardView);
        moveInfoTextView = findViewById(R.id.moveInfoTextView);
        analysisTextView = findViewById(R.id.analysisTextView);
        prevButton = findViewById(R.id.prevMoveButton);
        nextButton = findViewById(R.id.nextMoveButton);
        analyzeButton = findViewById(R.id.analyzeButton);

        // Get move history from intent - with extra logging
        moveHistory = getIntent().getStringArrayListExtra("MOVE_HISTORY");
        if (moveHistory == null) {
            moveHistory = new ArrayList<>();
            Log.d(TAG, "No move history received from intent");
        } else {
            Log.d(TAG, "Received move history with " + moveHistory.size() + " moves");
            for (int i = 0; i < moveHistory.size(); i++) {
                Log.d(TAG, "Move " + (i+1) + ": " + moveHistory.get(i));
            }
        }

        // Initialize the engine with detailed logging
        try {
            initializeStockfishEngine();

            // Generate all positions from the moves
            generatePositions();

            // Set up initial position
            updateToPosition(0);

            // Set up button listeners
            prevButton.setOnClickListener(v -> showPreviousMove());
            nextButton.setOnClickListener(v -> showNextMove());
            analyzeButton.setOnClickListener(v -> analyzeCurrentPosition());
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing analysis: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        // Check if we're loading a saved game
        long gameId = getIntent().getLongExtra("GAME_ID", -1);
        if (gameId != -1) {
            // We're loading a saved game
            loadSavedGame(gameId);
        } else {
            // Normal behavior - loading from move history passed in intent
            moveHistory = getIntent().getStringArrayListExtra("MOVE_HISTORY");
            if (moveHistory == null) {
                moveHistory = new ArrayList<>();
            }
            generatePositions();
            updateToPosition(0);
        }

    }

    /**
     * Initialize the Stockfish chess engine
     */
    private void initializeStockfishEngine() {
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

            Log.d(TAG, "Initializing Stockfish engine from: " + engineFile.getAbsolutePath());

            // Initialize the engine
            engine = new StockfishManager();
            if (engine.startEngine(engineFile.getAbsolutePath())) {
                // Set a higher skill level for analysis
                engine.setSkillLevel(20); // Use maximum strength for analysis
                engine.newGame();
                Log.d(TAG, "Engine initialized successfully");
            } else {
                Toast.makeText(this, "Failed to start Stockfish engine",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing engine", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Generate FEN positions for each move in the game
     */
    private void generatePositions() {
        Log.d(TAG, "Generating positions for " + moveHistory.size() + " moves");

        // Create array for all positions (initial + after each move)
        positions = new String[moveHistory.size() + 1];

        // Starting position is always the same
        positions[0] = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        Log.d(TAG, "Position 0 (initial): " + positions[0]);

        try {
            // Reset engine to initial position
            engine.newGame();
            engine.setPositionFromMoves();

            // For each move, apply it and save resulting position
            for (int i = 0; i < moveHistory.size(); i++) {
                // Build the list of moves up to this point
                List<String> movesUpToNow = moveHistory.subList(0, i + 1);
                String[] movesArray = movesUpToNow.toArray(new String[0]);

                // Apply moves and get position
                engine.setPositionFromMoves(movesArray);
                positions[i + 1] = engine.getCurrentFEN();

                Log.d(TAG, "Position " + (i+1) + " after move " + moveHistory.get(i) + ": " + positions[i+1]);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating positions", e);
            Toast.makeText(this, "Error preparing positions: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Update the board to a specific position in the game
     */
    @SuppressLint("SetTextI18n")
    private void updateToPosition(int index) {
        if (index < 0 || index > moveHistory.size()) {
            Log.e(TAG, "Invalid position index: " + index);
            return;
        }

        Log.d(TAG, "Updating to position at index " + index);
        currentMoveIndex = index;

        try {
            // Update the board with the FEN for this position
            String fen = positions[index];
            Log.d(TAG, "Setting board to FEN: " + fen);
            boardView.updateBoardFromFen(fen);

            // Update the move info
            if (index == 0) {
                moveInfoTextView.setText("Initial Position");
            } else {
                int moveNumber = (index + 1) / 2;
                boolean isWhiteMove = (index % 2 == 1);
                String moveText = String.format("Move %d%s: %s",
                        moveNumber,
                        isWhiteMove ? "" : "...",
                        convertToAlgebraic(moveHistory.get(index - 1)));
                moveInfoTextView.setText(moveText);
                Log.d(TAG, "Set move text to: " + moveText);
            }

            // Enable/disable navigation buttons
            prevButton.setEnabled(index > 0);
            nextButton.setEnabled(index < moveHistory.size());

            // Clear previous analysis
            analysisTextView.setText("");
        } catch (Exception e) {
            Log.e(TAG, "Error updating to position", e);
            Toast.makeText(this, "Error showing position: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show the previous move in the game
     */
    private void showPreviousMove() {
        if (currentMoveIndex > 0) {
            updateToPosition(currentMoveIndex - 1);
        }
    }

    /**
     * Show the next move in the game
     */
    private void showNextMove() {
        if (currentMoveIndex < moveHistory.size()) {
            updateToPosition(currentMoveIndex + 1);
        }
    }

    /**
     * Analyze the current position with Stockfish - Enhanced Version
     */
    private void analyzeCurrentPosition() {
        Log.d(TAG, "Analyzing current position at index: " + currentMoveIndex);

        try {
            if (currentMoveIndex == moveHistory.size()) {
                analysisTextView.setText("Game ended. No further moves to analyze.");
                return;
            }

            // Show that analysis is in progress
            analysisTextView.setText("Analyzing position... please wait");
            String currentFen = positions[currentMoveIndex];
            Log.d(TAG, "Setting engine to analyze position: " + currentFen);

            // Set up the position to analyze
            engine.setPosition(currentFen);

            // Run analysis in a background thread
            new Thread(() -> {
                try {
                    // Get actual move played
                    String actualMove = currentMoveIndex < moveHistory.size() ?
                            moveHistory.get(currentMoveIndex) : "";
                    Log.d(TAG, "Actual move played: " + actualMove);

                    // Run analysis
                    engine.sendCommand("go depth 15 movetime 2000");

                    // Wait for analysis and get best move
                    String bestMove = waitForBestMove(3000);
                    Log.d(TAG, "Best move from analysis: " + bestMove);

                    // Also get the output buffer for more details
                    List<String> outputBuffer = engine.getOutputBuffer();

                    // Get evaluation score if available
                    float evaluation = 0.0f;
                    for (String line : outputBuffer) {
                        if (line.contains("score cp ")) {
                            try {
                                int scoreIndex = line.indexOf("score cp ") + 9;
                                int endIndex = line.indexOf(" ", scoreIndex);
                                if (endIndex > scoreIndex) {
                                    evaluation = Float.parseFloat(line.substring(scoreIndex, endIndex)) / 100.0f;
                                    break;
                                }
                            } catch (Exception e) {
                                // Continue if this line fails to parse
                            }
                        }
                    }

                    // Build a more detailed analysis
                    StringBuilder analysisBuilder = new StringBuilder();

                    // Header showing move and evaluation
                    analysisBuilder.append("Position Analysis\n");
                    analysisBuilder.append("----------------\n\n");

                    // Add evaluation
                    if (evaluation > 0) {
                        analysisBuilder.append("Evaluation: +").append(String.format("%.2f", evaluation))
                                .append(" (White advantage)\n\n");
                    } else if (evaluation < 0) {
                        analysisBuilder.append("Evaluation: ").append(String.format("%.2f", evaluation))
                                .append(" (Black advantage)\n\n");
                    } else {
                        analysisBuilder.append("Evaluation: 0.00 (Equal position)\n\n");
                    }

                    // Best move section
                    analysisBuilder.append("Best Move: ").append(convertToAlgebraic(bestMove)).append("\n");

                    // Your move comparison
                    analysisBuilder.append("Your move: ").append(convertToAlgebraic(actualMove)).append("\n\n");

                    if (bestMove.equals(actualMove)) {
                        analysisBuilder.append("Excellent! You found the best move! 🌟\n");
                    } else {
                        analysisBuilder.append("There's a stronger move available.\n");
                    }

                    // Add positional advice based on game phase
                    int moveNumber = (currentMoveIndex / 2) + 1;
                    if (moveNumber <= 10) {
                        // Opening advice
                        analysisBuilder.append("\nOpening Tip: ");
                        if (moveNumber <= 3) {
                            analysisBuilder.append("Focus on controlling the center with pawns and developing your knights early.");
                        } else if (moveNumber <= 6) {
                            analysisBuilder.append("Try to castle early to keep your king safe and connect your rooks.");
                        } else {
                            analysisBuilder.append("Complete your development and look for tactical opportunities.");
                        }
                    } else if (moveNumber <= 25) {
                        // Middlegame advice
                        analysisBuilder.append("\nMiddlegame Tip: ");
                        analysisBuilder.append("Look for piece coordination and potential tactical shots. Consider your pawn structure and piece activity.");
                    } else {
                        // Endgame advice
                        analysisBuilder.append("\nEndgame Tip: ");
                        analysisBuilder.append("Activate your king and try to create passed pawns. Centralize your pieces for maximum effectiveness.");
                    }

                    final String analysis = analysisBuilder.toString();

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        analysisTextView.setText(analysis);
                        Log.d(TAG, "Analysis complete, displayed results");
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error during analysis", e);
                    runOnUiThread(() -> {
                        Toast.makeText(GameAnalysisActivity.this,
                                "Analysis error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        analysisTextView.setText("Analysis failed. Please try again. Error: " + e.getMessage());
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Error initiating analysis", e);
            analysisTextView.setText("Could not start analysis: " + e.getMessage());
        }
    }

    /**
     * Convert UCI format to algebraic notation
     */
    private String convertToAlgebraic(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) return "?";

        // Get source and destination squares
        char fromFile = uciMove.charAt(0);
        int fromRank = Character.getNumericValue(uciMove.charAt(1));
        char toFile = uciMove.charAt(2);
        int toRank = Character.getNumericValue(uciMove.charAt(3));

        // Simplified algebraic notation - just show the destination square for pawns
        // or piece letter + destination for other pieces
        try {
            // Get the piece type from the current board
            char piece = ' ';
            int fromRow = 8 - fromRank;
            int fromCol = fromFile - 'a';

            // Check if we can get the piece from the board view
            if (boardView != null) {
                piece = boardView.getPieceAt(fromRow, fromCol);
            }

            if (piece == 'p' || piece == 'P' || piece == ' ') {
                // For pawns or unknown pieces, just show the destination square
                return "" + toFile + toRank;
            } else {
                // For other pieces, show the piece letter + destination
                char pieceChar = ' ';
                switch (Character.toUpperCase(piece)) {
                    case 'R': pieceChar = 'R'; break;
                    case 'N': pieceChar = 'N'; break;
                    case 'B': pieceChar = 'B'; break;
                    case 'Q': pieceChar = 'Q'; break;
                    case 'K': pieceChar = 'K'; break;
                    default: return toFile + "" + toRank; // Default to just the destination
                }

                return pieceChar + "" + toFile + "" + toRank;
            }
        } catch (Exception e) {
            // If anything goes wrong, just return the destination square
            Log.e(TAG, "Error converting to algebraic notation", e);
            return toFile + "" + toRank;
        }
    }

    /**
     * A simple helper method to wait for best move
     */
    private String waitForBestMove(int timeoutMs) throws IOException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        List<String> buffer = new ArrayList<>();

        while (System.currentTimeMillis() < endTime) {
            // Get the current output buffer from the engine
            List<String> currentBuffer = engine.getOutputBuffer();
            for (String line : currentBuffer) {
                if (!buffer.contains(line)) {
                    buffer.add(line);

                    if (line.startsWith("bestmove")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            return parts[1];
                        }
                    }
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // If we timed out, try stopping the analysis
        engine.sendCommand("stop");
        return "none";
    }

    private void loadSavedGame(long gameId) {
        GameDatabaseHelper dbHelper = new GameDatabaseHelper(this);
        GameDatabaseHelper.SavedGame savedGame = dbHelper.getGame(gameId);

        if (savedGame != null) {
            // Set the move history from the saved game
            moveHistory = new ArrayList<>(savedGame.getMoves());

            // Generate positions based on these moves
            generatePositions();

            // Update the display to show the initial position
            updateToPosition(0);

            Toast.makeText(this, "Loaded: " + savedGame.getDescription(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not load the saved game", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) {
            engine.stopEngine();
        }
    }
}