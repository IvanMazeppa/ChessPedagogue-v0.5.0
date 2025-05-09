package com.example.chesspedagogue;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static SoundPool soundPool;
    private static Map<String, Integer> soundMap = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize(Context context) {
        if (initialized) return;

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(attributes)
                .build();

        // Load sounds
        soundMap.put("move", soundPool.load(context, R.raw.move, 1));
        soundMap.put("capture", soundPool.load(context, R.raw.capture, 1));
        soundMap.put("check", soundPool.load(context, R.raw.check, 1));

        initialized = true;
    }

    public static void playSound(String sound) {
        if (!initialized) return;
        Integer soundId = soundMap.get(sound);
        if (soundId != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public static void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        initialized = false;
    }
}