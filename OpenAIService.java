package com.example.chesspedagogue;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Enhanced service for communicating with OpenAI API
 * Now with improved context tracking and conversation continuity
 */
public class OpenAIService {
    private static final String TAG = "OpenAIService";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static OpenAIService instance;
    private final OkHttpClient client;
    private final Gson gson;
    private String apiKey;

    // Model to use - can be changed based on your preference
    private String model = "gpt-4-turbo-preview";

    // Chess context for continuity in conversations
    private final List<Message> conversationHistory = new ArrayList<>();
    private static final int MAX_CONVERSATION_LENGTH = 10;

    // Enhanced context tracking
    private Map<String, Object> playerContext = new HashMap<>();
    private List<String> conceptsExplained = new ArrayList<>();
    private List<String> playerMistakes = new ArrayList<>();
    private String currentFEN = "";
    private String playerColor = "white";

    private OpenAIService() {
        // Configure OkHttpClient with timeouts
        client = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        gson = new GsonBuilder().create();

        // Initialize conversation with system message defining the coach's role
        initializeSystemMessage();
    }

    /**
     * Initialize or reset the system message with the base coach personality
     */
    private void initializeSystemMessage() {
        conversationHistory.clear();

        Message systemMessage = new Message("system",
                "You are an encouraging and patient chess coach named Coach Tal. " +
                        "You provide clear, concise advice about chess positions and strategies " +
                        "in an accessible way. Tailor your advice to beginners and intermediate players. " +
                        "Use simple language and explain chess concepts briefly. " +
                        "Be encouraging even when pointing out mistakes. " +
                        "Keep responses under 3 sentences when possible.");

        conversationHistory.add(systemMessage);
    }

    public static synchronized OpenAIService getInstance() {
        if (instance == null) {
            instance = new OpenAIService();
        }
        return instance;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Update the context with new information about the player or game
     */
    public void updateContext(String key, Object value) {
        playerContext.put(key, value);
        updateSystemMessage();
    }

    /**
     * Record a chess concept that has been explained to avoid repetition
     */
    public void recordConceptExplained(String concept) {
        if (!conceptsExplained.contains(concept)) {
            conceptsExplained.add(concept);
            // Keep the list manageable
            if (conceptsExplained.size() > 15) {
                conceptsExplained.remove(0);
            }
            updateSystemMessage();
        }
    }

    /**
     * Record a mistake pattern the player is making
     */
    public void recordPlayerMistake(String mistakeType) {
        playerMistakes.add(mistakeType);
        // Keep only the 5 most recent mistakes
        if (playerMistakes.size() > 5) {
            playerMistakes.remove(0);
        }
        updateSystemMessage();
    }

    /**
     * Update the system message with all current context
     */
    private void updateSystemMessage() {
        if (conversationHistory.isEmpty()) {
            initializeSystemMessage();
            return;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Coach Tal, a patient and encouraging chess coach. ");
        prompt.append("Your goal is to help the player improve while maintaining a warm, supportive tone. ");

        // Add current position information if available
        if (!currentFEN.isEmpty()) {
            prompt.append("The current board position in FEN notation is: ").append(currentFEN).append(". ");
            prompt.append("The player is playing as ").append(playerColor).append(". ");
        }

        // Add information about player's skill level if known
        if (playerContext.containsKey("skillLevel")) {
            prompt.append("The player is at a ").append(playerContext.get("skillLevel"))
                    .append(" skill level. ");
        }

        // Add information about player's recent struggles
        if (!playerMistakes.isEmpty()) {
            prompt.append("The player has recently struggled with: ");
            prompt.append(String.join(", ", playerMistakes));
            prompt.append(". Be attentive to these areas without being repetitive. ");
        }

        // Add information about what's been covered
        if (!conceptsExplained.isEmpty()) {
            prompt.append("You've already explained these concepts: ");
            prompt.append(String.join(", ", conceptsExplained));
            prompt.append(". You can reference them but avoid re-explaining unless asked. ");
        }

        // Add any other context keys
        for (Map.Entry<String, Object> entry : playerContext.entrySet()) {
            if (!entry.getKey().equals("skillLevel")) { // Already handled above
                prompt.append(entry.getKey()).append(": ").append(entry.getValue()).append(". ");
            }
        }

        // Update the first message which should be the system message
        conversationHistory.set(0, new Message("system", prompt.toString()));
    }

    /**
     * Sends a user message to the API and returns the response
     */
    public String sendMessage(String userMessage) {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API key not set");
            return "Error: API key not configured.";
        }

        try {
            // Add the user message to the conversation history
            conversationHistory.add(new Message("user", userMessage));

            // Trim conversation if it gets too long
            if (conversationHistory.size() > MAX_CONVERSATION_LENGTH + 1) { // +1 for system message
                conversationHistory.subList(1, 2).clear(); // Remove oldest user/assistant pair
            }

            // Create the API request
            ChatRequest chatRequest = new ChatRequest(model, conversationHistory, 150);
            String requestJson = gson.toJson(chatRequest);

            RequestBody body = RequestBody.create(requestJson, JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                if (response.body() != null) {
                    Log.e(TAG, "API Error: " + response.body().string());
                }
                return "Sorry, I had trouble connecting to my chess brain. Please try again.";
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return "Sorry, I received an empty response. Please try again.";
            }

            // Parse the response
            String responseJson = responseBody.string();
            ChatResponse chatResponse = gson.fromJson(responseJson, ChatResponse.class);

            if (chatResponse != null && chatResponse.choices != null && !chatResponse.choices.isEmpty()) {
                String assistantResponse = chatResponse.choices.get(0).message.content;

                // Add the assistant's response to the conversation history
                conversationHistory.add(new Message("assistant", assistantResponse));

                // Check for concepts explained
                checkForConceptsExplained(assistantResponse);

                return assistantResponse;
            } else {
                return "Sorry, I couldn't generate a response. Please try again.";
            }

        } catch (IOException e) {
            Log.e(TAG, "Error sending message to OpenAI", e);
            return "Sorry, there was a problem communicating with the chess coach. Please check your internet connection.";
        }
    }

    /**
     * Check a response for chess concepts that should be recorded as explained
     */
    private void checkForConceptsExplained(String response) {
        String responseLower = response.toLowerCase();

        // Check for common chess concepts
        if (responseLower.contains("pin") || responseLower.contains("pinned")) {
            recordConceptExplained("pins");
        }
        if (responseLower.contains("fork") || responseLower.contains("forking")) {
            recordConceptExplained("forks");
        }
        if (responseLower.contains("skewer")) {
            recordConceptExplained("skewers");
        }
        if (responseLower.contains("discovered") && (responseLower.contains("check") ||
                responseLower.contains("attack"))) {
            recordConceptExplained("discovered attacks");
        }
        if (responseLower.contains("doubled") && responseLower.contains("pawn")) {
            recordConceptExplained("doubled pawns");
        }
        if (responseLower.contains("isolated") && responseLower.contains("pawn")) {
            recordConceptExplained("isolated pawns");
        }
        if (responseLower.contains("castl")) {
            recordConceptExplained("castling");
        }
        if (responseLower.contains("develop") &&
                (responseLower.contains("piece") || responseLower.contains("knight") ||
                        responseLower.contains("bishop"))) {
            recordConceptExplained("development");
        }
        if (responseLower.contains("center") || responseLower.contains("central")) {
            recordConceptExplained("center control");
        }
        // Add more concept detection as needed
    }

    /**
     * Generate chess advice based on the current position
     */
    public String generateChessAdvice(String fen, String lastMove, String playerColor) {
        this.currentFEN = fen;
        this.playerColor = playerColor;

        String prompt = "The current chess position in FEN notation is: " + fen + ". ";

        if (lastMove != null && !lastMove.isEmpty()) {
            prompt += "The last move was " + lastMove + ". ";
        }

        prompt += "I'm playing as " + playerColor + ". ";
        prompt += "Please give me brief advice about my position and what I should be focusing on.";

        return sendMessage(prompt);
    }

    /**
     * Generate enhanced chess advice with full game context
     */
    public String generateEnhancedChessAdvice(String fen, List<String> moveHistory, String playerColor) {
        this.currentFEN = fen;
        this.playerColor = playerColor;

        StringBuilder prompt = new StringBuilder();
        prompt.append("I'm analyzing a chess game in progress.\n\n");
        prompt.append("Current position (FEN): ").append(fen).append("\n\n");

        // Add move history with proper formatting
        if (moveHistory != null && !moveHistory.isEmpty()) {
            prompt.append("Game moves so far:\n");
            int moveNum = 1;
            for (int i = 0; i < moveHistory.size(); i += 2) {
                prompt.append(moveNum).append(". ");
                prompt.append(moveHistory.get(i));
                if (i + 1 < moveHistory.size()) {
                    prompt.append(" ").append(moveHistory.get(i + 1));
                }
                prompt.append("\n");
                moveNum++;
            }
        }

        prompt.append("\nI'm playing as ").append(playerColor);
        prompt.append(".\n\nPlease analyze my position and suggest what I should focus on next. Consider the opening principles, piece development, pawn structure, tactical opportunities, and my overall strategic direction.");

        return sendMessage(prompt.toString());
    }

    /**
     * Evaluate a specific move
     */
    public String evaluateMove(String fen, String move, String playerColor) {
        this.currentFEN = fen;
        this.playerColor = playerColor;

        String prompt = "In this chess position: " + fen + ", ";
        prompt += "I'm playing as " + playerColor + " and considering the move " + move + ". ";
        prompt += "Is this a good move? Why or why not? Please be concise.";

        return sendMessage(prompt);
    }

    /**
     * Reset the conversation history, keeping only the system message
     */
    public void resetConversation() {
        Message systemMessage = conversationHistory.isEmpty() ?
                new Message("system", "") : conversationHistory.get(0);

        conversationHistory.clear();
        conversationHistory.add(systemMessage);

        // Don't reset context tracking - we want to remember concepts explained
        // across conversation resets
    }

    // Request and response classes for OpenAI API
    private static class Message {
        @SerializedName("role")
        String role;

        @SerializedName("content")
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ChatRequest {
        @SerializedName("model")
        String model;

        @SerializedName("messages")
        List<Message> messages;

        @SerializedName("max_tokens")
        int maxTokens;

        public ChatRequest(String model, List<Message> messages, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.maxTokens = maxTokens;
        }
    }


    private ChatRequest createChatRequest(String userMessage) {
        // Add the user message to the conversation history
        conversationHistory.add(new Message("user", userMessage));

        // Add a system instruction for brevity
        // This temporary message doesn't get stored in conversation history
        List<Message> requestMessages = new ArrayList<>(conversationHistory);
        requestMessages.add(new Message("system",
                "Keep your response brief and focused - ideally 2-3 sentences. Be concise but helpful."));

        // Create the API request with max tokens limit
        return new ChatRequest(model, requestMessages, 150); // Limit to ~150 tokens
    }

    private static class ChatResponse {
        @SerializedName("choices")
        List<Choice> choices;

        private static class Choice {
            @SerializedName("message")
            Message message;
        }
    }
}