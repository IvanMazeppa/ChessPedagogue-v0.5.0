package com.example.chesspedagogue;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class SavedGamesActivity extends AppCompatActivity {
    private GameDatabaseHelper dbHelper;
    private ListView gamesListView;
    private TextView emptyTextView;
    private List<GameDatabaseHelper.SavedGame> savedGames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_games);

        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);

        // Instead, set the navigation icon manually:
        toolbar.setNavigationIcon(android.R.drawable.ic_lock_lock);
        toolbar.setNavigationOnClickListener(view -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        gamesListView = findViewById(R.id.gamesListView);
        emptyTextView = findViewById(R.id.emptyTextView);

        // Initialize database helper
        dbHelper = new GameDatabaseHelper(this);

        // Load saved games
        loadSavedGames();

        // Set item click listener
        gamesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GameDatabaseHelper.SavedGame selectedGame = savedGames.get(position);
                openGameReview(selectedGame.getId());
            }
        });
    }

    private void loadSavedGames() {
        savedGames = dbHelper.getAllGames();

        if (savedGames.isEmpty()) {
            gamesListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            gamesListView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);

            // Create a list of game descriptions for the adapter
            List<String> gameDescriptions = new ArrayList<>();
            for (GameDatabaseHelper.SavedGame game : savedGames) {
                String desc = game.getFormattedDate() + "\n" +
                        "Playing as: " + game.getPlayerColor() + "\n" +
                        game.getDescription();
                gameDescriptions.add(desc);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    gameDescriptions);

            gamesListView.setAdapter(adapter);
        }
    }

    // In SavedGamesActivity.java, update the openGameReview method:

    private void openGameReview(long gameId) {
        // Get the game details
        GameDatabaseHelper.SavedGame game = dbHelper.getGame(gameId);

        if (game == null) return;

        // Ask if the user wants to continue playing or analyze
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load Game");
        String[] options = {"Continue Playing", "Analyze Game"};

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // Continue playing
                    Intent intent = new Intent(SavedGamesActivity.this, MainActivity.class);
                    intent.putExtra("LOAD_GAME_ID", game.getId());
                    intent.putExtra("PLAYER_COLOR", game.getPlayerColor());
                    intent.putStringArrayListExtra("MOVE_HISTORY", new ArrayList<>(game.getMoves()));
                    intent.putExtra("FINAL_FEN", game.getFinalFen());
                    startActivity(intent);
                    finish();
                } else {
                    // Analyze game
                    Intent intent = new Intent(SavedGamesActivity.this, GameAnalysisActivity.class);
                    intent.putExtra("GAME_ID", game.getId());
                    startActivity(intent);
                }
            }
        });

        builder.show();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}