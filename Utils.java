package com.example.chesspedagogue;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Utils {
    private static final String TAG = "Utils";

    /**
     * Copies an asset file to the app's private directory and makes it executable
     *
     * @param context The application context
     * @param assetName The name of the asset in the assets folder
     * @param outputName The name to give the extracted file
     * @return The file object pointing to the extracted executable, or null if failed
     */
    public static File copyAssetToExecutableDir(Context context, String assetName, String outputName) throws IOException {
        File outputDir = new File(context.getFilesDir(), "bin");
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + outputDir.getAbsolutePath());
                return null;
            }
        }

        File outputFile = new File(outputDir, outputName);

        // If the file already exists, no need to extract it again
        if (outputFile.exists()) {
            return outputFile;
        }

        try (InputStream in = context.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        // Make sure the file is executable
        if (!outputFile.setExecutable(true)) {
            Log.w(TAG, "Could not set executable permission on: " + outputFile.getAbsolutePath());
        }

        return outputFile;
    }
}