// Create this new file: ChatMessage.java
package com.example.chesspedagogue;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_COACH = 1;

    private int type;
    private String message;
    private long timestamp;

    public ChatMessage(int type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public int getType() { return type; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
}