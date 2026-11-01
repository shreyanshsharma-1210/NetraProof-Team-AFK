package com.emergency.patient.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.emergency.patient.models.LesionCategory;
import com.emergency.patient.models.LesionMaskData;

import java.util.ArrayList;
import java.util.List;

/**
 * RetinaOverlayView — Medical imaging canvas component for NetraProof.
 *
 * Capabilities:
 *  1. Renders high-resolution fundus base image with strict aspect-ratio preservation (fitCenter).
 *  2. Composites four independent lesion segmentation masks (U-Net + SE-ResNeXt50)
 *     with custom category tinting, independent layer toggles, and variable opacity blending.
 *  3. Supports three visualization modes:
 *     - OVERLAY: Single interactive canvas with mask blending and opacity control.
 *     - SIDE_BY_SIDE: Synchronously scaled split comparison (Original vs Segmented).
 *     - BEFORE_AFTER: Single-canvas toggle between pristine original and segmented views.
 *  4. Provides pinch-to-zoom (1.0x to 5.0x), programmatic zoom (+ / - / reset), and 2D panning.
 */
public class RetinaOverlayView extends View {

    public enum DisplayMode {
        OVERLAY,
        SIDE_BY_SIDE,
        BEFORE_AFTER
    }

    // ─── Rendering Resources ──────────────────────────────────────────────────
    private Bitmap baseBitmap = null;
    private final List<LesionMaskData> lesionMasks = new ArrayList<>();

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ─── Display & Opacity State ──────────────────────────────────────────────
    private DisplayMode displayMode = DisplayMode.OVERLAY;
    private float overlayOpacity = 0.65f; // Default 65% transparency per clinical recommendation
    private boolean beforeAfterShowOverlay = true;

    // ─── Transform & Zoom State ───────────────────────────────────────────────
    private float scaleFactor = 1.0f;
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;

    private float translationX = 0f;
    private float translationY = 0f;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isPanning = false;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // ─── Reusable Geometries ──────────────────────────────────────────────────
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final RectF leftDstRect = new RectF();
    private final RectF rightDstRect = new RectF();

    // ─── Constructors ─────────────────────────────────────────────────────────

    public RetinaOverlayView(Context context) {
        super(context);
        init(context);
    }

    public RetinaOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RetinaOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.parseColor("#0A151C"));

        dividerPaint.setColor(Color.parseColor("#40FFFFFF"));
        dividerPaint.setStrokeWidth(3f);

        labelBgPaint.setColor(Color.parseColor("#B010202A"));
        labelBgPaint.setStyle(Paint.Style.FILL);

        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(spToPx(11f));
        labelTextPaint.setFakeBoldText(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    // ─── Data & Configuration Setters ─────────────────────────────────────────

    public void setBaseBitmap(@Nullable Bitmap bitmap) {
        this.baseBitmap = bitmap;
        resetTransform();
        invalidate();
    }

    public void setLesionMasks(@NonNull List<LesionMaskData> masks) {
        this.lesionMasks.clear();
        this.lesionMasks.addAll(masks);
        invalidate();
    }

    public void setOverlayOpacity(float opacity) {
        this.overlayOpacity = Math.max(0.10f, Math.min(1.0f, opacity));
        invalidate();
    }

    public float getOverlayOpacity() {
        return overlayOpacity;
    }

    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        invalidate();
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setBeforeAfterShowOverlay(boolean showOverlay) {
        this.beforeAfterShowOverlay = showOverlay;
        invalidate();
    }

    public boolean isBeforeAfterShowingOverlay() {
        return beforeAfterShowOverlay;
    }

    // ─── Zoom Controls ────────────────────────────────────────────────────────

    public void zoomIn() {
        setScale(Math.min(scaleFactor * 1.35f, MAX_SCALE));
    }

    public void zoomOut() {
        setScale(Math.max(scaleFactor / 1.35f, MIN_SCALE));
    }

    public void resetZoom() {
        resetTransform();
        invalidate();
    }

    private void setScale(float newScale) {
        this.scaleFactor = newScale;
        if (scaleFactor <= 1.0f) {
            translationX = 0f;
            translationY = 0f;
        } else {
            clampTranslation();
        }
        invalidate();
    }

    private void resetTransform() {
        scaleFactor = 1.0f;
        translationX = 0f;
        translationY = 0f;
    }

    private void clampTranslation() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float maxTransX = (w * (scaleFactor - 1.0f)) / 2f;
        float maxTransY = (h * (scaleFactor - 1.0f)) / 2f;

        translationX = Math.max(-maxTransX, Math.min(maxTransX, translationX));
        translationY = Math.max(-maxTransY, Math.min(maxTransY, translationY));
    }

    // ─── Touch Handling ───────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isPanning = scaleFactor > 1.0f;
                // Disallow parent ScrollView from intercepting while interacting with image
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPanning && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    translationX += dx;
                    translationY += dy;
                    clampTranslation();
                    invalidate();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPanning = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));
            clampTranslation();
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (scaleFactor > 1.0f) {
                resetZoom();
            } else {
                scaleFactor = 2.0f;
                clampTranslation();
                invalidate();
            }
            return true;
        }
    }

    // ─── Canvas Drawing ───────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (baseBitmap == null || baseBitmap.isRecycled()) {
            drawEmptyPlaceholder(canvas);
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        canvas.save();

        // Apply scale & pan transformation centered on canvas
        canvas.translate(viewWidth / 2f + translationX, viewHeight / 2f + translationY);
        canvas.scale(scaleFactor, scaleFactor);
        canvas.translate(-viewWidth / 2f, -viewHeight / 2f);

        switch (displayMode) {
            case SIDE_BY_SIDE:
                drawSideBySide(canvas, viewWidth, viewHeight);
                break;

            case BEFORE_AFTER:
                drawBeforeAfter(canvas, viewWidth, viewHeight);
                break;

            case OVERLAY:
            default:
                drawOverlay(canvas, viewWidth, viewHeight);
                break;
        }

        canvas.restore();
    }

    /**
     * Standard interactive overlay: pristine base retinal image + active tinted masks.
     */
    private void drawOverlay(Canvas canvas, int width, int height) {
        calculateFitCenterRect(baseBitmap.getWidth(), baseBitmap.getHeight(), width, height, dstRect);

        srcRect.set(0, 0, baseBitmap.getWidth(), baseBitmap.getHeight());
        canvas.drawBitmap(baseBitmap, srcRect, dstRect, bitmapPaint);

        drawMaskLayers(canvas, dstRect);
    }

    /**
     * Side-by-side mode: Left half = Original fundus; Right half = Segmented overlay.
     */
    private void drawSideBySide(Canvas canvas, int width, int height) {
        int halfWidth = width / 2;

        // Left viewport: Original Fundus
        calculateFitCenterRect(baseBitmap.getWidth(), baseBitmap.getHeight(), halfWidth - 8, height, leftDstRect);
        leftDstRect.offset(4, 0);

        srcRect.set(0, 0, baseBitmap.getWidth(), baseBitmap.getHeight());
        canvas.drawBitmap(baseBitmap, srcRect, leftDstRect, bitmapPaint);
        drawViewportBadge(canvas, leftDstRect.left + 12, leftDstRect.top + 16, "ORIGINAL FUNDUS");

        // Center split divider line
        canvas.drawLine(halfWidth, 16, halfWidth, height - 16, dividerPaint);

        // Right viewport: Segmented View
        calculateFitCenterRect(baseBitmap.getWidth(), baseBitmap.getHeight(), halfWidth - 8, height, rightDstRect);
        rightDstRect.offset(halfWidth + 4, 0);

        canvas.drawBitmap(baseBitmap, srcRect, rightDstRect, bitmapPaint);
        drawMaskLayers(canvas, rightDstRect);
        drawViewportBadge(canvas, rightDstRect.left + 12, rightDstRect.top + 16, "SEGMENTED OVERLAY");
    }

    /**
     * Before/After comparison mode: Shows either 100% original or segmented overlay based on toggle.
     */
    private void drawBeforeAfter(Canvas canvas, int width, int height) {
        calculateFitCenterRect(baseBitmap.getWidth(), baseBitmap.getHeight(), width, height, dstRect);

        srcRect.set(0, 0, baseBitmap.getWidth(), baseBitmap.getHeight());
        canvas.drawBitmap(baseBitmap, srcRect, dstRect, bitmapPaint);

        if (beforeAfterShowOverlay) {
            drawMaskLayers(canvas, dstRect);
            drawViewportBadge(canvas, dstRect.left + 16, dstRect.top + 20, "VIEW: AI OVERLAY ENABLED");
        } else {
            drawViewportBadge(canvas, dstRect.left + 16, dstRect.top + 20, "VIEW: PRISTINE ORIGINAL");
        }
    }

    /**
     * Iterates through active lesion categories and draws enabled masks with appropriate color filter and opacity.
     */
    private void drawMaskLayers(Canvas canvas, RectF targetBounds) {
        if (lesionMasks.isEmpty()) return;

        for (LesionMaskData mask : lesionMasks) {
            if (!mask.isVisible() || !mask.isAvailable()) continue;

            Bitmap maskBitmap = mask.getMaskBitmap();
            if (maskBitmap == null || maskBitmap.isRecycled()) continue;

            LesionCategory category = mask.getCategory();
            int color = ContextCompat.getColor(getContext(), category.getColorResId());

            maskPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
            maskPaint.setAlpha((int) (overlayOpacity * 255));

            srcRect.set(0, 0, maskBitmap.getWidth(), maskBitmap.getHeight());
            canvas.drawBitmap(maskBitmap, srcRect, targetBounds, maskPaint);
        }
    }

    /**
     * Draws an informational pill label inside the image viewport.
     */
    private void drawViewportBadge(Canvas canvas, float x, float y, String text) {
        float paddingH = dpToPx(8f);
        float paddingV = dpToPx(4f);
        float textWidth = labelTextPaint.measureText(text);
        Paint.FontMetrics fm = labelTextPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        RectF badgeRect = new RectF(x, y, x + textWidth + (paddingH * 2), y + textHeight + (paddingV * 2));
        canvas.drawRoundRect(badgeRect, dpToPx(6f), dpToPx(6f), labelBgPaint);
        canvas.drawText(text, x + paddingH, y + paddingV - fm.ascent, labelTextPaint);
    }

    /**
     * Calculates bounds maintaining exact aspect ratio centered inside the container.
     */
    private void calculateFitCenterRect(int srcW, int srcH, int boundsW, int boundsH, RectF outRect) {
        if (srcW <= 0 || srcH <= 0 || boundsW <= 0 || boundsH <= 0) {
            outRect.set(0, 0, boundsW, boundsH);
            return;
        }

        float scale = Math.min((float) boundsW / srcW, (float) boundsH / srcH);
        float drawnW = srcW * scale;
        float drawnH = srcH * scale;

        float left = (boundsW - drawnW) / 2f;
        float top = (boundsH - drawnH) / 2f;

        outRect.set(left, top, left + drawnW, top + drawnH);
    }

    private void drawEmptyPlaceholder(Canvas canvas) {
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#71818D"));
        textPaint.setTextSize(spToPx(13f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText("No retinal fundus photograph available", cx, cy, textPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}

