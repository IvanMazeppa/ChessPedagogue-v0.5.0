package com.example.chesspedagogue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Secure handling of API keys
 */
public class ApiKeyConfig {
    private static final String TAG = "ApiKeyConfig";
    private static final String PREFS_NAME = "ChessPedagoguePrefs";
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";

    /**
     * Save the OpenAI API key securely
     */
    public static void saveApiKey(Context context, String apiKey) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_OPENAI_API_KEY, apiKey);
            editor.apply();

            Log.d(TAG, "API key saved successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error saving API key", e);
        }
    }

    /**
     * Retrieve the OpenAI API key
     */
    public static String getApiKey(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(KEY_OPENAI_API_KEY, null);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving API key", e);
            return null;
        }
    }

    /**
     * Check if an API key is already saved
     */
    public static boolean hasApiKey(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.contains(KEY_OPENAI_API_KEY);
        } catch (Exception e) {
            Log.e(TAG, "Error checking for API key", e);
            return false;
        }
    }

    /**
     * Clear the saved API key
     */
    public static void clearApiKey(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_OPENAI_API_KEY);
            editor.apply();

            Log.d(TAG, "API key cleared successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing API key", e);
        }
    }
}