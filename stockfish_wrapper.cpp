#include <string>
#include <thread>
#include <mutex>
#include <queue>
#include <condition_variable>
#include <iostream>
#include <sstream>
#include <android/log.h>
#include "stockfish_wrapper.h"

// External functions from stockfish_adapter.cpp
extern "C" {
namespace Stockfish {
    void init();
    void engine_exit();
    bool isready();
    void ucinewgame();
    void position(const char* fen);
    void setoption(const char* name, const char* value);
    void go(int depth, int movetime);
    const char* bestmove();
    void stop();
}
}

// Define logging macros
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "StockfishWrapper", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StockfishWrapper", __VA_ARGS__)

// Internal state
static std::thread engine_thread;
static bool engine_running = false;
static std::mutex engine_mutex;
static std::condition_variable engine_cv;
static std::queue<std::string> command_queue;
static std::string latest_response;

// Thread function to process Stockfish commands
static void process_commands() {
    LOGD("Stockfish command processing thread started");

    // Initialize the Stockfish engine
    Stockfish::init();

    while (engine_running) {
        std::string cmd;

        // Wait for a command
        {
            std::unique_lock<std::mutex> lock(engine_mutex);
            engine_cv.wait(lock, [] {
                return !command_queue.empty() || !engine_running;
            });

            if (!engine_running) break;

            cmd = command_queue.front();
            command_queue.pop();
        }

        LOGD("Processing command: %s", cmd.c_str());

        // Process the command
        std::stringstream response;

        if (cmd == "isready") {
            bool ready = Stockfish::isready();
            response << (ready ? "readyok" : "not ready");
        }
        else if (cmd == "ucinewgame") {
            Stockfish::ucinewgame();
            response << "ok";
        }
        else if (cmd.substr(0, 8) == "position ") {
            std::string fen = cmd.substr(9);
            Stockfish::position(fen.c_str());
            response << "ok";
        }
        else if (cmd.substr(0, 10) == "setoption ") {
            // Parse "setoption name NAME value VALUE"
            std::string remaining = cmd.substr(10);
            size_t name_pos = remaining.find("name ");
            size_t value_pos = remaining.find(" value ");

            if (name_pos != std::string::npos && value_pos != std::string::npos) {
                std::string name = remaining.substr(name_pos + 5, value_pos - name_pos - 5);
                std::string value = remaining.substr(value_pos + 7);
                Stockfish::setoption(name.c_str(), value.c_str());
                response << "ok";
            } else {
                response << "error: invalid setoption format";
            }
        }
        else if (cmd.substr(0, 3) == "go ") {
            // Parse "go [depth D] [movetime M]"
            int depth = 0;
            int movetime = 1000; // Default 1 second

            size_t depth_pos = cmd.find("depth ");
            if (depth_pos != std::string::npos) {
                depth = std::stoi(cmd.substr(depth_pos + 6));
            }

            size_t time_pos = cmd.find("movetime ");
            if (time_pos != std::string::npos) {
                movetime = std::stoi(cmd.substr(time_pos + 9));
            }

            Stockfish::go(depth, movetime);
            response << "bestmove " << Stockfish::bestmove();
        }
        else if (cmd == "stop") {
            Stockfish::stop();
            response << "ok";
        }
        else if (cmd == "uci") {
            response << "id name Stockfish" << std::endl;
            response << "id author Stockfish Team" << std::endl;
            // Add UCI options here if needed
            response << "uciok";
        }
        else {
            response << "unknown command: " << cmd;
        }

        // Store the response
        {
            std::lock_guard<std::mutex> lock(engine_mutex);
            latest_response = response.str();
            LOGD("Command response: %s", latest_response.c_str());
        }
    }

    // Cleanup Stockfish
    Stockfish::engine_exit();
    LOGD("Stockfish command processing thread exiting");
}

// Implementation of stockfish_init
bool stockfish_init() {
    LOGD("Initializing Stockfish wrapper");

    if (engine_running) {
        LOGD("Stockfish already running");
        return true;
    }

    engine_running = true;

    try {
        // Start the command processing thread
        engine_thread = std::thread(process_commands);

        // Make sure the engine is ready
        std::string response = stockfish_command("isready");
        return response == "readyok";
    }
    catch (const std::exception& e) {
        LOGE("Exception in stockfish_init: %s", e.what());
        engine_running = false;
        return false;
    }
}

// Implementation of stockfish_command
std::string stockfish_command(const std::string& cmd) {
    if (!engine_running) {
        LOGE("Stockfish engine not running");
        return "Engine not running";
    }

    try {
        LOGD("Sending command: %s", cmd.c_str());

        // Clear previous response
        {
            std::lock_guard<std::mutex> lock(engine_mutex);
            latest_response = "";
        }

        // Add the command to the queue
        {
            std::lock_guard<std::mutex> lock(engine_mutex);
            command_queue.push(cmd);
        }

        // Notify the processing thread
        engine_cv.notify_one();

        // Wait a reasonable time for a response (can be adjusted)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        // Get the response
        std::string response;
        {
            std::lock_guard<std::mutex> lock(engine_mutex);
            response = latest_response;
        }

        return response;
    }
    catch (const std::exception& e) {
        LOGE("Exception in stockfish_command: %s", e.what());
        return std::string("Error: ") + e.what();
    }
}

// Implementation of stockfish_quit
void stockfish_quit() {
    LOGD("Shutting down Stockfish wrapper");

    if (engine_running) {
        // Signal the thread to exit
        {
            std::lock_guard<std::mutex> lock(engine_mutex);
            engine_running = false;
        }

        // Wake up the thread if it's waiting
        engine_cv.notify_one();

        // Wait for the thread to finish
        if (engine_thread.joinable()) {
            engine_thread.join();
        }

        // Clear the command queue
        std::queue<std::string> empty;
        std::swap(command_queue, empty);

        LOGD("Stockfish wrapper shutdown complete");
    }
}