MEDICATION REMINDER SOUND
=========================

File required: medication_reminder.mp3

This file is used as the custom notification sound for the
"Medication Dose Reminders" notification channel (med_reminder_channel).

Requirements:
- Format: MP3 (or OGG/WAV — Android supports all three in res/raw/)
- Duration: 2–5 seconds recommended
- Name: medication_reminder  (no extension change needed)
- Generic alert sound suitable for all medicines

IMPORTANT:
The placeholder file committed here is intentionally empty.
Replace it with a real MP3 before building a release APK.
If the file is empty/corrupt, Android will fall back to the
system default notification sound automatically — the app
will NOT crash.
