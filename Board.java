package com.example.chesspedagogue;

/**
 * A simple chess board model for piece placement and move execution.
 * (This class is optional since we use chesslib for logic, but provided for completeness.)
 */
public class Board {
    // 8x8 board state (initial position). Uppercase = White, lowercase = Black, space = empty.
    private char[][] boardState = {
            {'r','n','b','q','k','b','n','r'},
            {'p','p','p','p','p','p','p','p'},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {' ',' ',' ',' ',' ',' ',' ',' '},
            {'P','P','P','P','P','P','P','P'},
            {'R','N','B','Q','K','B','N','R'}
    };

    // Game state for FEN generation
    private boolean whiteToMove = true;
    private boolean whiteCastleKing = true, whiteCastleQueen = true;
    private boolean blackCastleKing = true, blackCastleQueen = true;
    private String enPassantTarget = "-";
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    // Get a copy of the current board state
    public char[][] getBoardState() {
        char[][] copy = new char[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(boardState[i], 0, copy[i], 0, 8);
        }
        return copy;
    }

    // Make a move given in UCI format (e.g., "e2e4" or "e7e8q"). Returns true if the move was applied.
    public boolean makeMove(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) {
            return false;
        }
        int fromCol = uciMove.charAt(0) - 'a';
        int fromRow = 8 - Character.getNumericValue(uciMove.charAt(1));
        int toCol   = uciMove.charAt(2) - 'a';
        int toRow   = 8 - Character.getNumericValue(uciMove.charAt(3));
        // Validate coordinates
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
                toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) {
            return false;
        }
        char piece = boardState[fromRow][fromCol];
        if (piece == ' ') {
            return false; // no piece to move
        }
        // Check if a piece will be captured at the destination
        char capturedPiece = boardState[toRow][toCol];

        // Remove the moving piece from its original square
        boardState[fromRow][fromCol] = ' ';

        // Handle castling if a king moves two files
        if ((piece == 'K' || piece == 'k') && Math.abs(fromCol - toCol) == 2) {
            if (toCol == 6) {
                // Kingside castling
                if (piece == 'K') {
                    // White king: move rook from h1 to f1
                    boardState[7][5] = 'R';
                    boardState[7][7] = ' ';
                } else {
                    // Black king: move rook from h8 to f8
                    boardState[0][5] = 'r';
                    boardState[0][7] = ' ';
                }
            } else if (toCol == 2) {
                // Queenside castling
                if (piece == 'K') {
                    // White king: move rook from a1 to d1
                    boardState[7][3] = 'R';
                    boardState[7][0] = ' ';
                } else {
                    // Black king: move rook from a8 to d8
                    boardState[0][3] = 'r';
                    boardState[0][0] = ' ';
                }
            }
        }

        // Place the moving piece on the destination (handle pawn promotion)
        if (piece == 'P' || piece == 'p') {
            if (uciMove.length() == 5) {
                // Promotion specified (e.g., e7e8q)
                char promoChar = uciMove.charAt(4);
                boardState[toRow][toCol] = Character.isUpperCase(piece) ? Character.toUpperCase(promoChar)
                        : Character.toLowerCase(promoChar);
            } else if ((piece == 'P' && toRow == 0) || (piece == 'p' && toRow == 7)) {
                // Pawn reached last rank with no promotion specified – promote to Queen
                boardState[toRow][toCol] = Character.isUpperCase(piece) ? 'Q' : 'q';
            } else {
                // Normal pawn move
                boardState[toRow][toCol] = piece;
            }
        } else {
            // Normal move for other pieces
            boardState[toRow][toCol] = piece;
        }

        // Update castling rights if necessary
        if (piece == 'K') {
            whiteCastleKing = false;
            whiteCastleQueen = false;
        } else if (piece == 'k') {
            blackCastleKing = false;
            blackCastleQueen = false;
        }
        if (piece == 'R') {
            if (fromRow == 7 && fromCol == 0) whiteCastleQueen = false;
            if (fromRow == 7 && fromCol == 7) whiteCastleKing = false;
        } else if (piece == 'r') {
            if (fromRow == 0 && fromCol == 0) blackCastleQueen = false;
            if (fromRow == 0 && fromCol == 7) blackCastleKing = false;
        }
        if (capturedPiece == 'R') {
            if (toRow == 7 && toCol == 0) whiteCastleQueen = false;
            if (toRow == 7 && toCol == 7) whiteCastleKing = false;
        } else if (capturedPiece == 'r') {
            if (toRow == 0 && toCol == 0) blackCastleQueen = false;
            if (toRow == 0 && toCol == 7) blackCastleKing = false;
        }

        // Update en passant target square
        enPassantTarget = "-";
        if (Character.toLowerCase(piece) == 'p' && Math.abs(fromRow - toRow) == 2) {
            int epRow = (fromRow + toRow) / 2;
            char file = (char) ('a' + fromCol);
            enPassantTarget = "" + file + (8 - epRow);
        }

        // Update halfmove clock (reset if pawn move or capture)
        if (Character.toLowerCase(piece) == 'p' || capturedPiece != ' ') {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }

        // Update fullmove number and side to move
        boolean wasWhiteTurn = whiteToMove;
        whiteToMove = !whiteToMove;
        if (!wasWhiteTurn) {
            fullmoveNumber++;
        }

        return true;
    }

    // Generate a FEN string for the current board state
    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        // Piece placement
        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                char piece = boardState[row][col];
                if (piece == ' ') {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(piece);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < 7) fen.append('/');
        }
        // Active color
        fen.append(' ').append(whiteToMove ? 'w' : 'b');
        // Castling rights
        fen.append(' ');
        StringBuilder castling = new StringBuilder();
        if (whiteCastleKing) castling.append('K');
        if (whiteCastleQueen) castling.append('Q');
        if (blackCastleKing) castling.append('k');
        if (blackCastleQueen) castling.append('q');
        fen.append(castling.length() == 0 ? "-" : castling.toString());
        // En passant target square
        fen.append(' ').append(enPassantTarget);
        // Halfmove clock and fullmove number
        fen.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return fen.toString();
    }
}
