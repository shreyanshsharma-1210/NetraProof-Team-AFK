package com.emergency.patient.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * ImageUtils — Prepares and compresses fundus photos locally before uploading.
 *
 * Compresses large 12–48 MP smartphone captures down to a max 1280px dimension at 90% JPEG quality,
 * producing optimal ~250 KB files for sub-second network screening.
 */
public final class ImageUtils {

    private static final String TAG = "ImageUtils";
    private static final int MAX_DIMENSION = 1280;
    private static final int JPEG_QUALITY = 90;

    private ImageUtils() {}

    /**
     * Compresses a photo from a content Uri to a cached JPEG file.
     */
    public static File compressForScreening(Context context, Uri photoUri) throws Exception {
        if (context == null || photoUri == null) {
            throw new IllegalArgumentException("Context and photoUri must not be null");
        }

        // Decode bounds to determine sample size
        BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
        boundsOpts.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(photoUri)) {
            BitmapFactory.decodeStream(is, null, boundsOpts);
        }

        int origW = boundsOpts.outWidth;
        int origH = boundsOpts.outHeight;

        int sampleSize = 1;
        while (origW / (sampleSize * 2) >= MAX_DIMENSION || origH / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sampleSize;
        Bitmap sampledBitmap;
        try (InputStream is = context.getContentResolver().openInputStream(photoUri)) {
            sampledBitmap = BitmapFactory.decodeStream(is, null, decodeOpts);
        }

        if (sampledBitmap == null) {
            throw new IllegalStateException("Failed to decode bitmap from Uri: " + photoUri);
        }

        // Apply scale down to max 1280px if still larger
        int w = sampledBitmap.getWidth();
        int h = sampledBitmap.getHeight();
        Bitmap finalBitmap = sampledBitmap;

        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            float ratio = Math.min((float) MAX_DIMENSION / w, (float) MAX_DIMENSION / h);
            int targetW = Math.round(w * ratio);
            int targetH = Math.round(h * ratio);
            finalBitmap = Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true);
            if (finalBitmap != sampledBitmap) {
                sampledBitmap.recycle();
            }
        }

        // Handle EXIF orientation if available
        try (InputStream exifStream = context.getContentResolver().openInputStream(photoUri)) {
            if (exifStream != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                ExifInterface exif = new ExifInterface(exifStream);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                int rotation = 0;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;

                if (rotation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotation);
                    Bitmap rotated = Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.getWidth(), finalBitmap.getHeight(), matrix, true);
                    if (rotated != finalBitmap) {
                        finalBitmap.recycle();
                        finalBitmap = rotated;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not inspect EXIF orientation: " + e.getMessage());
        }

        // Write compressed output file
        File cacheDir = new File(context.getCacheDir(), "netra_upload");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File outputFile = new File(cacheDir, "fundus_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            fos.flush();
        } finally {
            finalBitmap.recycle();
        }

        Log.i(TAG, "Compressed fundus photo to: " + outputFile.length() / 1024 + " KB");
        return outputFile;
    }

    /**
     * Compresses a photo from an existing File.
     */
    public static File compressForScreening(Context context, File sourceFile) throws Exception {
        return compressForScreening(context, Uri.fromFile(sourceFile));
    }

    /**
     * Prepares MultipartBody.Part from a photo Uri.
     */
    public static MultipartBody.Part prepareImageForUpload(Context context, Uri photoUri) throws Exception {
        File compressedFile = compressForScreening(context, photoUri);
        MediaType mediaType = MediaType.parse("image/jpeg");
        RequestBody reqFile = RequestBody.create(mediaType, compressedFile);
        return MultipartBody.Part.createFormData("file", "fundus.jpg", reqFile);
    }

    /**
     * Prepares MultipartBody.Part from a local File.
     */
    public static MultipartBody.Part prepareImageForUpload(Context context, File file) throws Exception {
        File compressedFile = compressForScreening(context, file);
        MediaType mediaType = MediaType.parse("image/jpeg");
        RequestBody reqFile = RequestBody.create(mediaType, compressedFile);
        return MultipartBody.Part.createFormData("file", "fundus.jpg", reqFile);
    }
}

