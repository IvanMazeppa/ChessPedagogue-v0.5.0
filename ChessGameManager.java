package com.example.chesspedagogue;

import java.util.ArrayList;
import java.util.List;

public class ChessGameManager {
    private StockfishManager engine;
    private List<String> moveHistory = new ArrayList<>();

    public ChessGameManager(StockfishManager engine) {
        this.engine = engine;
        // Initialize a new game
        engine.newGame();
        engine.setPositionFromMoves(); // Set up the starting position
    }

    public void makeMove(String move) {
        moveHistory.add(move);
        // Update the engine's position
        engine.setPositionFromMoves(moveHistory.toArray(new String[0]));
    }

    /**
     * Get a copy of the current move history.
     * This is useful for analysis and reviewing the game.
     *
     * @return A copy of the current move history
     */
    public List<String> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    public String getCurrentFEN() {
        return engine.getCurrentFEN();
    }

    public void newGame() {
        moveHistory.clear();
        engine.newGame();
        engine.setPositionFromMoves();
    }
}