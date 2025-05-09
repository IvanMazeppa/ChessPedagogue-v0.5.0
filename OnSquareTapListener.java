package com.example.chesspedagogue;

// Listener interface for square taps
public interface OnSquareTapListener {
    void onSquareTapped(int row, int col);
    // Add this new method to handle deselection
    void onSquareReselected(); // Called when user taps same square again
}