#include "stockfish_wrapper.h"
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <sstream>
#include <iostream>
#include <android/log.h>

// For Android logging
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "StockfishNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StockfishNative", __VA_ARGS__)

// Forward declaration of Stockfish's main function
int stockfish_main(int argc, char* argv[]);

// Global variables for communication
static std::thread engine_thread;
static std::mutex cmd_mutex;
static std::condition_variable cmd_cv;
static std::queue<std::string> cmd_queue;
static std::mutex resp_mutex;
static std::condition_variable resp_cv;
static std::queue<std::string> resp_queue;
static bool engine_running = false;

// Custom stringstream to capture Stockfish's output
class CaptureStream : public std::stringstream {
public:
    CaptureStream() : std::stringstream() {}

    // Override the sync method to capture output
    virtual int sync() {
        std::string output = this->str();
        this->str("");

        if (!output.empty()) {
            std::lock_guard<std::mutex> lock(resp_mutex);
            resp_queue.push(output);
            resp_cv.notify_one();
        }

        return 0;
    }
};

// Static storage for capturing stdout/stderr
static CaptureStream capture_out;
static std::streambuf* old_cout = nullptr;
static std::streambuf* old_cerr = nullptr;

// Function to run Stockfish in a separate thread
void run_engine() {
    try {
        // Redirect stdout and stderr to our capture stream
        old_cout = std::cout.rdbuf(capture_out.rdbuf());
        old_cerr = std::cerr.rdbuf(capture_out.rdbuf());

        // Prepare arguments for Stockfish
        char* argv[] = {const_cast<char*>("stockfish")};

        // Start Stockfish in UCI mode
        stockfish_main(1, argv);
    }
    catch (const std::exception& e) {
        LOGE("Exception in engine thread: %s", e.what());
    }

    // Restore stdout and stderr
    if (old_cout) std::cout.rdbuf(old_cout);
    if (old_cerr) std::cerr.rdbuf(old_cerr);
}

// Implementation of the interface functions
bool stockfish_init() {
    if (engine_running) return true;

    try {
        engine_running = true;
        engine_thread = std::thread(run_engine);

        // Wait for the engine to start up
        std::this_thread::sleep_for(std::chrono::milliseconds(500));

        // Send the UCI command to initialize
        stockfish_command("uci");

        return true;
    }
    catch (const std::exception& e) {
        LOGE("Error initializing Stockfish: %s", e.what());
        engine_running = false;
        return false;
    }
}

std::string stockfish_command(const std::string& cmd) {
    if (!engine_running) {
        return "Engine not running";
    }

    LOGD("Sending command: %s", cmd.c_str());

    // Send the command by writing to stdin
    std::cout << cmd << std::endl;

    // For commands that generate a specific response, wait for it
    if (cmd == "uci" || cmd == "isready" || cmd.find("go ") == 0) {
        std::string terminator;
        if (cmd == "uci") terminator = "uciok";
        else if (cmd == "isready") terminator = "readyok";
        else if (cmd.find("go ") == 0) terminator = "bestmove";

        // Wait for the response with the terminator
        std::string response;
        auto timeout = std::chrono::system_clock::now() + std::chrono::seconds(30);

        while (response.find(terminator) == std::string::npos) {
            std::unique_lock<std::mutex> lock(resp_mutex);

            // Check for timeout
            if (std::chrono::system_clock::now() > timeout) {
                LOGE("Timeout waiting for response to: %s", cmd.c_str());
                return response + "\nTIMEOUT";
            }

            // Wait for response with timeout
            auto wait_status = resp_cv.wait_until(lock, timeout, [] {
                return !resp_queue.empty();
            });

            if (wait_status) {
                response += resp_queue.front();
                resp_queue.pop();
            }
        }

        return response;
    }

    // For commands that don't have a specific response, just return
    return "Command sent";
}

void stockfish_quit() {
    if (!engine_running) return;

    try {
        // Send quit command to Stockfish
        std::cout << "quit" << std::endl;

        // Wait for the thread to finish
        if (engine_thread.joinable()) {
            engine_thread.join();
        }

        // Restore stdout and stderr
        if (old_cout) std::cout.rdbuf(old_cout);
        if (old_cerr) std::cerr.rdbuf(old_cerr);

        engine_running = false;
    }
    catch (const std::exception& e) {
        LOGE("Error shutting down Stockfish: %s", e.what());
    }
}