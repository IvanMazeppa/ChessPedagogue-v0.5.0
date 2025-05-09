#include <jni.h>
#include <string>
#include <android/log.h>
#include "stockfish_wrapper.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "ChessPedagogue", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ChessPedagogue", __VA_ARGS__)

extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_example_chesspedagogue_StockfishManager_nativeInit(JNIEnv* env, jobject /* this */) {
    LOGD("Initializing Stockfish");
    return stockfish_init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_chesspedagogue_StockfishManager_nativeSendCommand(JNIEnv* env, jobject /* this */, jstring cmd) {
    const char* cmd_chars = env->GetStringUTFChars(cmd, nullptr);
    std::string cmd_str(cmd_chars);
    env->ReleaseStringUTFChars(cmd, cmd_chars);

    LOGD("Sending command: %s", cmd_str.c_str());
    std::string response = stockfish_command(cmd_str);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_chesspedagogue_StockfishManager_nativeQuit(JNIEnv* env, jobject /* this */) {
    LOGD("Shutting down Stockfish");
    stockfish_quit();
}

JNIEXPORT jstring JNICALL
Java_com_example_chesspedagogue_MainActivity_getStockfishVersion(JNIEnv* env, jobject /* this */) {
    // Initialize the engine if needed
    stockfish_init();

    // Send the UCI command to get engine info
    std::string response = stockfish_command("uci");

    // Extract version information
    std::string version = "Stockfish";
    size_t id_pos = response.find("id name ");
    if (id_pos != std::string::npos) {
        size_t end_pos = response.find("\n", id_pos);
        if (end_pos != std::string::npos) {
            version = response.substr(id_pos + 8, end_pos - id_pos - 8);
        }
    }

    return env->NewStringUTF(version.c_str());
}
}