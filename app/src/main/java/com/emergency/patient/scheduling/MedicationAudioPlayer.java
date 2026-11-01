package com.emergency.patient.scheduling;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import com.emergency.patient.R;

/**
 * MedicationAudioPlayer — Singleton manager for medication reminder audio.
 * Allows the audio to be started and INSTANTLY stopped when the user dismisses
 * the notification, marks the dose as taken, or snoozes.
 */
public class MedicationAudioPlayer {

    private static final String TAG = "MedicationAudioPlayer";
    private static MediaPlayer sMediaPlayer;

    public static synchronized void play(Context context) {
        stop();
        if (context == null) return;
        try {
            sMediaPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.medication_reminder);
            if (sMediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    sMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build());
                } else {
                    sMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                }
                sMediaPlayer.setOnCompletionListener(mp -> stop());
                sMediaPlayer.start();
                Log.d(TAG, "Medication reminder audio started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play medication reminder audio", e);
        }
    }

    public static synchronized void stop() {
        if (sMediaPlayer != null) {
            try {
                if (sMediaPlayer.isPlaying()) {
                    sMediaPlayer.stop();
                }
                sMediaPlayer.reset();
                sMediaPlayer.release();
                Log.d(TAG, "Medication reminder audio stopped");
            } catch (Exception ignored) {}
            sMediaPlayer = null;
        }
    }

    public static synchronized boolean isPlaying() {
        try {
            return sMediaPlayer != null && sMediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }
}
