package com.emergency.patient.scheduling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * NotificationImageLoader — Safely loads a medicine image bitmap for use in
 * a notification BigPictureStyle.
 *
 * Supported URI schemes:
 *   - file:///data/user/0/.../med_images/med_xxx.jpg  (absolute path saved by MedicationConfirmationActivity)
 *   - /data/user/0/.../med_images/med_xxx.jpg         (raw absolute path string, no scheme)
 *   - content://...                                    (content provider URI from camera/gallery)
 *
 * Safety guarantees:
 *   - Returns null instead of throwing on any error
 *   - Downsamples to MAX_BITMAP_PX × MAX_BITMAP_PX before returning
 *     to avoid OOM in NotificationManager's limited Binder transaction buffer
 *   - Never blocks the main thread (caller must be on a worker thread)
 */
public final class NotificationImageLoader {

    private static final String TAG = "NotifImageLoader";

    /** Max pixel dimension for either axis of the notification large-icon bitmap. */
    private static final int MAX_BITMAP_PX = 512;

    private NotificationImageLoader() {}

    /**
     * Loads and downsamples a bitmap from the given URI/path string.
     *
     * @param context  Application context (needed for ContentResolver)
     * @param imageUri String from MedicationEntity.imageUri — may be null, empty,
     *                 a raw absolute path, a file:// URI, or a content:// URI.
     * @return Downsampled Bitmap or {@code null} if loading fails for any reason.
     */
    public static Bitmap load(Context context, String imageUri) {
        if (imageUri == null || imageUri.trim().isEmpty()) return null;

        try {
            // ── Normalise to Uri ─────────────────────────────────────────────
            Uri uri;
            if (imageUri.startsWith("content://") || imageUri.startsWith("file://")) {
                uri = Uri.parse(imageUri);
            } else {
                // Bare absolute path — wrap as file URI
                uri = Uri.fromFile(new File(imageUri));
            }

            // ── First pass: read dimensions only (inJustDecodeBounds) ────────
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            decodeBitmap(context, uri, opts);

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                Log.w(TAG, "Bitmap bounds unreadable for: " + imageUri);
                return null;
            }

            // ── Compute sample size ──────────────────────────────────────────
            opts.inSampleSize      = computeSampleSize(opts.outWidth, opts.outHeight);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig  = Bitmap.Config.RGB_565; // half the memory of ARGB_8888

            Bitmap bmp = decodeBitmap(context, uri, opts);
            if (bmp == null) {
                Log.w(TAG, "Bitmap decode returned null for: " + imageUri);
                return null;
            }

            // ── Final safety clamp ───────────────────────────────────────────
            if (bmp.getWidth() > MAX_BITMAP_PX || bmp.getHeight() > MAX_BITMAP_PX) {
                bmp = scaleBitmap(bmp);
            }

            return bmp;

        } catch (Exception e) {
            Log.e(TAG, "Failed to load notification image: " + imageUri, e);
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Bitmap decodeBitmap(Context context, Uri uri, BitmapFactory.Options opts) {
        String scheme = uri.getScheme();
        if ("content".equals(scheme)) {
            InputStream is = null;
            try {
                is = context.getContentResolver().openInputStream(uri);
                return (is != null) ? BitmapFactory.decodeStream(is, null, opts) : null;
            } catch (Exception e) {
                Log.e(TAG, "ContentResolver stream failed for: " + uri, e);
                return null;
            } finally {
                if (is != null) {
                    try { is.close(); } catch (IOException ignored) {}
                }
            }
        } else {
            // file:// — decode directly from path for speed
            String path = uri.getPath();
            if (path == null) return null;
            File f = new File(path);
            if (!f.exists() || !f.isFile()) {
                Log.w(TAG, "File does not exist: " + path);
                return null;
            }
            return BitmapFactory.decodeFile(path, opts);
        }
    }

    private static int computeSampleSize(int width, int height) {
        int sampleSize = 1;
        int halfWidth  = width  / 2;
        int halfHeight = height / 2;
        while ((halfWidth  / sampleSize) >= MAX_BITMAP_PX
            || (halfHeight / sampleSize) >= MAX_BITMAP_PX) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static Bitmap scaleBitmap(Bitmap source) {
        float scale = Math.min(
            (float) MAX_BITMAP_PX / source.getWidth(),
            (float) MAX_BITMAP_PX / source.getHeight()
        );
        int w = Math.round(source.getWidth()  * scale);
        int h = Math.round(source.getHeight() * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(source, w, h, true);
        if (scaled != source) source.recycle();
        return scaled;
    }
}
