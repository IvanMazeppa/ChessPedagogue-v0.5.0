package com.example.chesspedagogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
//import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChessConversationActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton voiceButton;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();

    private ChessCoachManager chessCoach;
    private SpeechRecognitionManager speechRecognitionManager;

    // Game state passed from main activity
    private String currentFen;
    private List<String> moveHistory;
    private String playerColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chess_conversation);

        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);

        // Instead, set the navigation icon manually:
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(view -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Get game state from intent
        Intent intent = getIntent();
        currentFen = intent.getStringExtra("FEN");
        moveHistory = intent.getStringArrayListExtra("MOVE_HISTORY");
        playerColor = intent.getStringExtra("PLAYER_COLOR");

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewChat);
        messageInput = findViewById(R.id.editTextMessage);
        sendButton = findViewById(R.id.buttonSend);
        voiceButton = findViewById(R.id.buttonVoiceInput);

        // Set up recycler view
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        recyclerView.setAdapter(adapter);

        // Initialize coach and speech recognition
        chessCoach = ChessCoachManager.getInstance(this);
        speechRecognitionManager = new SpeechRecognitionManager(this);

        // Set up click listeners
        sendButton.setOnClickListener(v -> sendMessage());
        voiceButton.setOnClickListener(v -> startVoiceRecognition());

        // Add welcome message
        addCoachMessage("Hello! I'm Coach Mikhail Tal. I'm here to help with your chess game. What would you like to know?");
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Add user message to chat
        addUserMessage(text);
        messageInput.setText("");

        //   message and get response
        chessCoach.sendMessage(text, new CoachResponseCallback());
    }

    private void startVoiceRecognition() {
        speechRecognitionManager.startListening(new SpeechRecognitionManager.SpeechRecognitionCallback() {
            @Override
            public void onSpeechRecognized(String text) {
                messageInput.setText(text);
                sendMessage();
            }

            @Override
            public void onSpeechError(String error) {
                Toast.makeText(ChessConversationActivity.this,
                        "Speech recognition error: " + error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addUserMessage(String text) {
        ChatMessage message = new ChatMessage(ChatMessage.TYPE_USER, text);
        adapter.addMessage(message);
        scrollToBottom();
    }

    private void addCoachMessage(String text) {
        ChatMessage message = new ChatMessage(ChatMessage.TYPE_COACH, text);
        adapter.addMessage(message);
        scrollToBottom();
    }

    private void scrollToBottom() {
        recyclerView.post(() -> recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognitionManager != null) {
            speechRecognitionManager.release();
        }
    }

    // Callback for coach responses
    private class CoachResponseCallback implements ChessCoachManager.ChessCoachCallback {
        @Override
        public void onResponseReceived(String response) {
            addCoachMessage(response);
        }

        @Override
        public void onError(String errorMessage) {
            Toast.makeText(ChessConversationActivity.this,
                    errorMessage, Toast.LENGTH_LONG).show();
            addCoachMessage("Sorry, I had trouble with that. Could you try asking again?");
        }

        @Override
        public void onSpeechCompleted() {
            // Nothing needed here
        }
    }
}