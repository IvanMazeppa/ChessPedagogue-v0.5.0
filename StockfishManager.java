package com.example.chesspedagogue;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages communication with the Stockfish chess engine using the UCI protocol.
 * Provides methods to send commands and receive responses.
 */
public class StockfishManager {
    private static final String TAG = "StockfishManager";
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;
    private final List<String> outputBuffer = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private boolean isReady = false;

    // Add this field to track the current FEN
    private String currentFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Starts the Stockfish engine process.
     *
     * @param enginePath Path to the Stockfish executable
     * @return true if the engine started successfully
     */
    public boolean startEngine(String enginePath) {
        try {
            // Start the engine using the provided path
            File engineFile = new File(enginePath);
            if (!engineFile.exists() || !engineFile.canExecute()) {
                Log.e(TAG, "Engine file does not exist or is not executable: " + enginePath);
                return false;
            }

            ProcessBuilder builder = new ProcessBuilder(enginePath);
            builder.redirectErrorStream(true);
            process = builder.start();

            // Set up communication channels
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // Start reader thread
            isRunning.set(true);
            readerThread = new Thread(this::readOutputContinuously);
            readerThread.start();

            // Initialize UCI mode
            sendCommand("uci");

            // Wait for "uciok" response
            if (!waitForResponse("uciok", 5000)) {
                Log.e(TAG, "Engine did not respond with 'uciok'");
                stopEngine();
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting engine", e);
            return false;
        }
    }


    /**
     * Continuously reads output from the engine process.
     */
    private void readOutputContinuously() {
        try {
            String line;
            while (isRunning.get() && (line = reader.readLine()) != null) {
                Log.d(TAG, "Engine: " + line);
                outputBuffer.add(line);

                // Check for readyok
                if (line.equals("readyok")) {
                    isReady = true;
                }
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "Error reading from engine", e);
            }
        } finally {
            Log.d(TAG, "Reader thread exiting");
        }
    }

    /**
     * Sends a command to the engine.
     *
     * @param command UCI command to send
     * @throws IOException if an I/O error occurs
     */
    public void sendCommand(String command) throws IOException {
        if (writer == null || !isRunning.get()) {
            throw new IOException("Engine not running");
        }

        Log.d(TAG, "Sending: " + command);
        writer.write(command + "\n");
        writer.flush();
    }

    /**
     * Waits for the engine to be ready.
     *
     * @param timeoutMs Timeout in milliseconds
     * @return true if the engine is ready within the timeout
     */
    public boolean waitForReady(long timeoutMs) {
        isReady = false;
        try {
            sendCommand("isready");
            return waitForResponse("readyok", timeoutMs);
        } catch (IOException e) {
            Log.e(TAG, "Error sending isready command", e);
            return false;
        }
    }

    /**
     * Waits for a specific response from the engine.
     *
     * @param responsePrefix The response prefix to wait for
     * @param timeoutMs      Timeout in milliseconds
     * @return true if the response was received within the timeout
     */
    public boolean waitForResponse(String responsePrefix, long timeoutMs) {
        long endTime = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < endTime) {
            for (String line : outputBuffer) {
                if (line.contains(responsePrefix)) {
                    return true;
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Gets the best move from the engine for the current position.
     *
     * @param thinkTimeMs Time in milliseconds for the engine to think
     * @return The best move in UCI notation (e.g., "e2e4") or null if no move was found
     */
    public String getBestMove(int thinkTimeMs) {
        try {
            // Clear output buffer
            outputBuffer.clear();

            // Send command to find best move with a specific time
            sendCommand("go movetime " + thinkTimeMs);

            // Wait for "bestmove" response
            long endTime = System.currentTimeMillis() + thinkTimeMs + 2000; // Add 2 seconds grace period
            String bestMove = null;

            // Keep checking for bestmove response
            while (System.currentTimeMillis() < endTime) {
                for (String line : outputBuffer) {
                    if (line.startsWith("bestmove")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            bestMove = parts[1];
                            break;
                        }
                    }
                }

                if (bestMove != null) {
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            return bestMove;
        } catch (IOException e) {
            Log.e(TAG, "Error getting best move", e);
            return null;
        }
    }

    /**
     * Sets the position on the internal engine board from FEN notation.
     *
     * @param fen FEN string representing the position
     * @return true if the position was set successfully
     */
    public boolean setPosition(String fen) {
        try {
            sendCommand("position fen " + fen);
            return waitForReady(1000);
        } catch (IOException e) {
            Log.e(TAG, "Error setting position", e);
            return false;
        }
    }

    public boolean setPositionFromMoves(String... moves) {
        try {
            StringBuilder command = new StringBuilder("position startpos");
            if (moves.length > 0) {
                command.append(" moves");
                for (String move : moves) {
                    command.append(" ").append(move);
                }
            }
            Log.d(TAG, "Setting position with command: " + command.toString());
            sendCommand(command.toString());

            // Verify the position was set correctly
            String fen = getCurrentFEN();
            Log.d(TAG, "Position set, resulting FEN: " + fen);

            return waitForReady(1000);
        } catch (IOException e) {
            Log.e(TAG, "Error setting position from moves", e);
            return false;
        }
    }

    /**
     * Sets the skill level of the engine (0-20).
     *
     * @param level Skill level (0 = weakest, 20 = strongest)
     * @return true if the skill level was set successfully
     */
    public boolean setSkillLevel(int level) {
        try {
            sendCommand("setoption name Skill Level value " + level);
            return waitForReady(1000);
        } catch (IOException e) {
            Log.e(TAG, "Error setting skill level", e);
            return false;
        }
    }

    // Add these methods to your existing StockfishManager.java class

    /**
     * Sets a UCI option for the engine.
     *
     * @param name Option name
     * @param value Option value
     * @return true if the option was set successfully
     */
    public boolean setOption(String name, String value) {
        try {
            sendCommand("setoption name " + name + " value " + value);
            return waitForReady(1000);
        } catch (IOException e) {
            Log.e(TAG, "Error setting option: " + name, e);
            return false;
        }
    }

    /**
     * Gets a detailed analysis of the current position.
     *
     * @param thinkTimeMs Time in milliseconds for the engine to analyze
     * @return Detailed analysis including multiple best moves and evaluations
     */
    public String getDetailedAnalysis(int thinkTimeMs) {
        try {
            // Clear output buffer
            outputBuffer.clear();

            // Tell engine to analyze
            Log.d(TAG, "Starting analysis with time: " + thinkTimeMs + "ms");
            sendCommand("go depth 15 multipv 3 movetime " + thinkTimeMs);

            // Wait for analysis to complete
            long endTime = System.currentTimeMillis() + thinkTimeMs + 1000;  // Add buffer
            boolean foundBestMove = false;

            while (System.currentTimeMillis() < endTime && !foundBestMove) {
                for (String line : outputBuffer) {
                    if (line.startsWith("bestmove")) {
                        foundBestMove = true;
                        break;
                    }
                }

                if (!foundBestMove) {
                    try {
                        Thread.sleep(50);  // Short sleep to prevent CPU spinning
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            // Stop analysis if it's still running
            if (!foundBestMove) {
                sendCommand("stop");
            }

            // Collect all the relevant output
            StringBuilder analysis = new StringBuilder();
            for (String line : outputBuffer) {
                if (line.contains("info depth") && line.contains("score") && line.contains("pv")) {
                    analysis.append(line).append("\n");
                }
            }

            Log.d(TAG, "Analysis complete, found " + analysis.toString().split("\n").length + " lines");
            return analysis.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error getting detailed analysis", e);
            return "Error analyzing position: " + e.getMessage();
        }
    }

    /**
     * Gets the current output buffer from the engine.
     * This is useful for analyzing the engine's responses.
     *
     * @return A copy of the current output buffer
     */
    public List<String> getOutputBuffer() {
        return new ArrayList<>(outputBuffer);
    }

    /**
     * Check if analysis is complete (bestmove received)
     */
    private boolean containsBestMove() {
        for (String line : outputBuffer) {
            if (line.startsWith("bestmove")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if analysis is complete (bestmove received)
     */
    private boolean isAnalysisDone() {
        for (String line : outputBuffer) {
            if (line.startsWith("bestmove")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the evaluation of a specific move.
     *
     * @param move The move to evaluate in UCI format
     * @param thinkTimeMs Time to analyze
     * @return The evaluation score in centipawns
     */
    public float evaluateMove(String move, int thinkTimeMs) {
        try {
            // Make the move
            String fen = getCurrentFEN();
            sendCommand("position fen " + fen + " moves " + move);

            // Clear output buffer
            outputBuffer.clear();

            // Analyze the resulting position
            sendCommand("go depth 16 movetime " + thinkTimeMs);

            // Wait for analysis to complete
            long endTime = System.currentTimeMillis() + thinkTimeMs + 2000;
            while (System.currentTimeMillis() < endTime && !isAnalysisDone()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Stop the analysis if it's still running
            sendCommand("stop");

            // Find the score
            float score = 0.0f;
            for (String line : outputBuffer) {
                if (line.contains("score cp ")) {
                    int scoreIndex = line.indexOf("score cp ") + 9;
                    int endIndex = line.indexOf(" ", scoreIndex);
                    if (endIndex > scoreIndex) {
                        try {
                            score = Float.parseFloat(line.substring(scoreIndex, endIndex)) / 100.0f;
                            // Negate score because we're looking from the opponent's perspective
                            score = -score;
                            break;
                        } catch (NumberFormatException e) {
                            // Skip this line
                        }
                    }
                }
            }

            // Restore the original position
            sendCommand("position fen " + fen);

            return score;
        } catch (IOException e) {
            Log.e(TAG, "Error evaluating move", e);
            return 0.0f;
        }
    }

    /**
     * Find the best move with an explanation of why it's good.
     *
     * @param thinkTimeMs Time to analyze
     * @return A description of the best move and why it's good
     */
    public String getBestMoveWithExplanation(int thinkTimeMs) {
        String bestMove = getBestMove(thinkTimeMs);
        if (bestMove == null || bestMove.isEmpty()) {
            return "No best move found";
        }

        float evaluation = evaluateMove(bestMove, thinkTimeMs / 2);

        StringBuilder explanation = new StringBuilder();
        explanation.append("Best move: ").append(bestMove);
        explanation.append(" (Evaluation: ").append(String.format("%.2f", evaluation)).append(")");

        // Add some basic positional understanding
        // (This would be expanded with more sophisticated pattern recognition)
        if (evaluation > 2.0) {
            explanation.append("\nThis move gives a winning advantage!");
        } else if (evaluation > 0.5) {
            explanation.append("\nThis move gives a clear advantage.");
        } else if (evaluation > 0.2) {
            explanation.append("\nThis move gives a slight advantage.");
        } else if (evaluation < -2.0) {
            explanation.append("\nTrying to minimize a losing position.");
        } else if (evaluation < -0.5) {
            explanation.append("\nTrying to equalize from a worse position.");
        } else {
            explanation.append("\nThis move keeps the position balanced.");
        }

        return explanation.toString();
    }
    /**
     * Stops the engine process.
     */
    public void stopEngine() {
        if (isRunning.get()) {
            isRunning.set(false);

            try {
                // Send quit command
                sendCommand("quit");

                // Wait for the process to exit
                process.waitFor(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.w(TAG, "Error stopping engine gracefully", e);
            } finally {
                // Force close everything
                try {
                    if (reader != null) reader.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing reader", e);
                }

                try {
                    if (writer != null) writer.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing writer", e);
                }

                if (process != null) {
                    process.destroy();
                }

                process = null;
                reader = null;
                writer = null;
            }
        }
    }

    /**
     * Tells the engine that a new game is about to begin.
     *
     * @return true if the command was successful
     */
    public boolean newGame() {
        try {
            sendCommand("ucinewgame");
            return waitForReady(1000);
        } catch (IOException e) {
            Log.e(TAG, "Error starting new game", e);
            return false;
        }
    }

    /**
     * Checks if a move is legal from the current position.
     */
    public boolean isLegalMove(String move) {
        try {
            // Set up the position and try the move
            String posCommand = "position fen " + currentFEN + " moves " + move;
            sendCommand(posCommand);

            // If the position is valid, we should get "readyok" when we check if ready
            return waitForReady(100);
        } catch (IOException e) {
            Log.e(TAG, "Error checking legal move", e);
            return false;
        }
    }

    public List<String> getLegalMovesForPiece(int row, int col) {
        List<String> moves = new ArrayList<>();
        try {
            // Get current FEN first
            String currentFen = getCurrentFEN();
            if (currentFen == null) return moves;

            // Convert board coordinates to algebraic
            char file = (char) ('a' + col);
            int rank = 8 - row;
            String square = "" + file + rank;

            Log.d(TAG, "Getting legal moves for piece at " + square);

            // We'll use a more efficient approach - get all legal moves from the position
            // and filter for ones that start from our square
            outputBuffer.clear();
            sendCommand("position fen " + currentFen);
            sendCommand("go perft 1");

            // Wait for response
            Thread.sleep(100);

            // Parse the output to find moves starting from our square
            for (String line : outputBuffer) {
                if (line.startsWith(square) ||
                        line.contains(" " + square) ||
                        line.contains(":" + square)) {

                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        if (part.startsWith(square) && part.length() >= 4) {
                            moves.add(part);
                            Log.d(TAG, "Found legal move: " + part);
                        }
                    }
                }
            }

            // If that didn't work, fall back to checking specific moves
            if (moves.isEmpty()) {
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        String move = "" + file + rank + (char)('a' + c) + (8 - r);
                        if (isLegalMove(move)) {
                            moves.add(move);
                            Log.d(TAG, "Found legal move (fallback): " + move);
                        }
                    }
                }
            }

            return moves;
        } catch (Exception e) {
            Log.e(TAG, "Error getting legal moves for piece", e);
            return moves;
        }
    }

    /**
     * Get the current position in FEN notation
     */
    public String getCurrentFEN() {
        try {
            outputBuffer.clear();
            sendCommand("d");

            // Wait for the response
            Thread.sleep(100);

            // Look for the FEN line in the output
            for (String line : outputBuffer) {
                if (line.startsWith("Fen: ")) {
                    currentFEN = line.substring(5).trim();
                    return currentFEN;
                }
            }

            return currentFEN; // Return the cached value if we can't get a new one
        } catch (Exception e) {
            Log.e(TAG, "Error getting current FEN", e);
            return currentFEN;
        }
    }
}