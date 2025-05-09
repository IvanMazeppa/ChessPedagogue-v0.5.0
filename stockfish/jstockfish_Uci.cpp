#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "StockfishNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

// This is the correct signature pattern:
// JNIEXPORT [return_type] JNICALL [function_name](JNIEnv*, jobject, ...)
JNIEXPORT jstring JNICALL
Java_com_example_jstockfish_StockfishNative_evaluatePosition(JNIEnv* env, jobject thiz, jstring fen) {
    LOGI("Native method called!");

    const char* nativeFen = env->GetStringUTFChars(fen, 0);
    std::string fenStr(nativeFen);
    env->ReleaseStringUTFChars(fen, nativeFen);

    LOGI("FEN string: %s", fenStr.c_str());

    // Simple placeholder response
    std::string result = "Position evaluated: " + fenStr;

    return env->NewStringUTF(result.c_str());
}

} // extern "C"