package com.example.chesspedagogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private RadioGroup colorRadioGroup;
    private SeekBar strengthSeekBar;
    private TextView strengthValueTextView;
    private Button startGameButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Find UI elements
        colorRadioGroup = findViewById(R.id.radioGroupColor);
        strengthSeekBar = findViewById(R.id.seekBarStrength);
        strengthValueTextView = findViewById(R.id.textViewStrengthValue);
        startGameButton = findViewById(R.id.buttonStartGame);

        // Configure engine strength slider (0 = weakest, 20 = strongest)
        strengthSeekBar.setMax(20);
        strengthSeekBar.setProgress(10);  // default mid-level
        // Show initial strength value with approximate Elo
        int initialSkill = strengthSeekBar.getProgress();
        int initialElo = 800 + initialSkill * 110;
        strengthValueTextView.setText("Engine Strength: ~" + initialElo + " Elo (Level " + initialSkill + ")");

        // Update displayed strength as the user adjusts the slider
        strengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int approxElo = 800 + progress * 110;
                strengthValueTextView.setText("Engine Strength: ~" + approxElo + " Elo (Level " + progress + ")");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // Start Game button launches the main game activity
        startGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Determine player color (default to white if none selected)
                String playerColor = "white";
                int selectedColorId = colorRadioGroup.getCheckedRadioButtonId();
                if (selectedColorId == R.id.radioBlack) {
                    playerColor = "black";
                }
                // Get selected engine strength level
                int skillLevel = strengthSeekBar.getProgress();
                // Launch MainActivity with the chosen options
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                intent.putExtra("PLAYER_COLOR", playerColor);
                intent.putExtra("SKILL_LEVEL", skillLevel);
                startActivity(intent);
                finish(); // close splash screen
            }
        });
    }
}
