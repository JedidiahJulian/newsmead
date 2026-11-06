package com.newsmead.research.study;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.UUID;

/** Separate-UID, local-only receiver. Uses platform/Java classes because the
 * standalone test APK does not inherit the target app's Kotlin runtime. */
public class StudyTransferReceiver extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final TextView status = new TextView(this);
        status.setText("Verifying local study transfer…");
        status.setTextSize(20f);
        setContentView(status);
        final Intent receivedIntent = getIntent();
        new Thread(() -> {
            String message;
            try {
                if (!Intent.ACTION_SEND.equals(receivedIntent.getAction()) ||
                        !"application/zip".equals(receivedIntent.getType())) throw new IllegalArgumentException("Expected ZIP share");
                @SuppressWarnings("deprecation")
                Uri uri = receivedIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalArgumentException("Expected content URI");
                File output = new File(getCacheDir(), "study-received-" + UUID.randomUUID() + ".zip");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long bytes = 0;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream target = new java.io.FileOutputStream(output)) {
                    if (input == null) throw new java.io.IOException("Shared URI unavailable");
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        target.write(buffer, 0, count); digest.update(buffer, 0, count); bytes += count;
                    }
                }
                StringBuilder hash = new StringBuilder();
                for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                JSONObject receipt = new JSONObject().put("verification", "separate_uid_local_share_receiver_v1")
                    .put("source_uri", uri.toString()).put("received_file", output.getName())
                    .put("byte_count", bytes).put("sha256", hash.toString()).put("network_transfer", false);
                try (OutputStream target = new java.io.FileOutputStream(new File(getCacheDir(), "study-receiver-receipt.json"))) {
                    target.write(receipt.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                message = "Received " + bytes + " bytes locally. Verify the computer copy before deleting any originals.";
            } catch (Exception error) {
                message = "Local transfer failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            final String finalMessage = message;
            runOnUiThread(() -> status.setText(finalMessage));
        }).start();
    }
}
