#include <iostream>
#include <string>
#include <cstring>
#include <thread>
#include <mutex>
#include <android/log.h>
#include "arch_compatibility.h"  // Add this before Stockfish headers

// Then include Stockfish headers
#include "bitboard.h"
#include "position.h"
// ... rest of includes

// Stockfish headers
#include "bitboard.h"
#include "position.h"
#include "search.h"
#include "thread.h"
#include "tt.h"
#include "uci.h"
#include "syzygy/tbprobe.h"

// For Android logging
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "StockfishAdapter", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StockfishAdapter", __VA_ARGS__)

using namespace Stockfish;

namespace {
    // Global state
    std::mutex engine_mutex;
    Search::LimitsType limits;
    Position pos;
    std::string current_fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    StateListPtr states;
    std::string best_move_string;
}

// Interface implementation for Stockfish UCI commands
extern "C" {
namespace Stockfish {
    void init() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Initializing Stockfish engine");

        // Initialize various Stockfish subsystems
        Bitboards::init();
        Position::init();
        Tune::init();
        Search::init();
        Threads.set(1);  // Single thread for mobile
        Search::clear();  // Clear any previous state
        TT.resize(16);  // 16 MB hash size for mobile

        // Setup default position
        states = StateListPtr(new std::deque<StateInfo>(1));
        pos = Position();
        pos.set(current_fen, false, &states->back());
    }

    void engine_exit() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Shutting down Stockfish engine");
        Threads.set(0);
    }

    bool isready() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        Search::clear();
        return true;
    }

    void ucinewgame() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("New game started");
        Search::clear();
        current_fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        states = StateListPtr(new std::deque<StateInfo>(1));
        pos = Position();
        pos.set(current_fen, false, &states->back());
    }

    void position(const char* fen) {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Setting position: %s", fen);
        current_fen = fen;
        states = StateListPtr(new std::deque<StateInfo>(1));
        pos = Position();
        pos.set(current_fen, false, &states->back());
    }

    void setoption(const char* name, const char* value) {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Setting option: %s = %s", name, value);

        if (strcmp(name, "Hash") == 0) {
            TT.resize(atoi(value));
        }
        else if (strcmp(name, "Threads") == 0) {
            int threads = atoi(value);
            threads = std::min(threads, 4);  // Cap at 4 threads for mobile
            Threads.set(threads);
        }
        else if (strcmp(name, "Skill Level") == 0) {
            int skill = atoi(value);
            if (skill <= 20) {
                // Configure Stockfish to play at appropriate skill level
                int margin = 25 * (20 - skill);
                limits.skill_level = skill;
                // Other skill related options would be set here
            }
        }
    }

    void go(int depth, int movetime) {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Starting search: depth=%d, movetime=%d", depth, movetime);

        // Setup search limits
        limits = Search::LimitsType();
        limits.depth = depth;
        limits.movetime = movetime;

        // Start search
        Threads.start_thinking(pos, states, limits);
        Threads.main()->wait_for_search_finished();

        // Get best move
        best_move_string = UCI::move(Threads.main()->rootMoves[0].pv[0], pos.is_chess960());
        LOGD("Best move: %s", best_move_string.c_str());
    }

    const char* bestmove() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        return best_move_string.c_str();
    }

    void stop() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        LOGD("Stopping search");
        Threads.stop = true;
    }
}
}