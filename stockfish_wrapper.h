#ifndef STOCKFISH_WRAPPER_H
#define STOCKFISH_WRAPPER_H

#include <string>

/**
 * Initializes the Stockfish engine.
 * This starts a background thread that communicates with the engine.
 *
 * @return true if the engine was initialized successfully, false otherwise
 */
bool stockfish_init();

/**
 * Sends a UCI command to Stockfish and gets the response.
 *
 * Supported commands include:
 * - "isready" - Checks if the engine is ready
 * - "ucinewgame" - Prepares for a new game
 * - "position fen [FEN]" - Sets the position from FEN
 * - "position startpos moves [move1] [move2]..." - Sets position after moves
 * - "setoption name [NAME] value [VALUE]" - Sets an engine option
 * - "go depth [D] movetime [M]" - Calculates best move
 * - "stop" - Stops the current calculation
 * - "uci" - Gets engine identity
 *
 * @param cmd The UCI command to send
 * @return The engine's response
 */
std::string stockfish_command(const std::string& cmd);

/**
 * Cleans up the Stockfish engine.
 * This stops the background thread and releases resources.
 */
void stockfish_quit();

#endif // STOCKFISH_WRAPPER_H