package com.emergency.patient.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;

/**
 * LiveEcgView — Medical-Grade Real-Time ECG Waveform Visualizer
 *
 * Features:
 * - High-precision canvas rendering with medical grid background (┼─┼─┼)
 * - Hardware-accelerated dynamic waveform streaming with physiological cardiac model
 * - Sweeping glowing scan head + real-time cardiac interpolation
 * - Robust bounded deque buffer to prevent memory thrash
 */
public class LiveEcgView extends View {

    private Paint gridPaintMajor;
    private Paint gridPaintMinor;
    private Paint ecgLinePaint;
    private Paint ecgGlowPaint;
    private Paint sweepDotPaint;

    private Path ecgPath = new Path();
    private ArrayDeque<Float> sampleBuffer = new ArrayDeque<>();
    private static final int MAX_SAMPLES = 280;

    private ValueAnimator animator;
    private int bpm = 72;
    private float phase = 0f;
    private long lastTime = 0;
    private boolean isRunning = false;

    public LiveEcgView(Context context) {
        super(context);
        init();
    }

    public LiveEcgView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveEcgView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Grid paints
        gridPaintMajor = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaintMajor.setColor(Color.parseColor("#CBE3D9"));
        gridPaintMajor.setStrokeWidth(1.2f);
        gridPaintMajor.setStyle(Paint.Style.STROKE);

        gridPaintMinor = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaintMinor.setColor(Color.parseColor("#E5F2EC"));
        gridPaintMinor.setStrokeWidth(0.8f);
        gridPaintMinor.setStyle(Paint.Style.STROKE);

        // ECG Line
        ecgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ecgLinePaint.setColor(Color.parseColor("#087F68"));
        ecgLinePaint.setStrokeWidth(2.8f);
        ecgLinePaint.setStyle(Paint.Style.STROKE);
        ecgLinePaint.setStrokeJoin(Paint.Join.ROUND);
        ecgLinePaint.setStrokeCap(Paint.Cap.ROUND);
        ecgLinePaint.setPathEffect(new CornerPathEffect(6f));

        // ECG Glow
        ecgGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ecgGlowPaint.setColor(Color.parseColor("#4414A66A"));
        ecgGlowPaint.setStrokeWidth(6.5f);
        ecgGlowPaint.setStyle(Paint.Style.STROKE);
        ecgGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        ecgGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        // Sweeping head dot
        sweepDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sweepDotPaint.setColor(Color.parseColor("#087F68"));
        sweepDotPaint.setStyle(Paint.Style.FILL);

        // Pre-fill buffer
        for (int i = 0; i < MAX_SAMPLES; i++) {
            sampleBuffer.add(getEcgValue((float) i / MAX_SAMPLES * 3f));
        }

        startAnimation();
    }

    public void setBpm(int newBpm) {
        this.bpm = Math.max(40, Math.min(180, newBpm));
    }

    public void startAnimation() {
        if (isRunning) return;
        isRunning = true;
        lastTime = SystemClock.uptimeMillis();

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            long now = SystemClock.uptimeMillis();
            long dt = Math.min(50, now - lastTime); // cap at 50ms to prevent jumps
            lastTime = now;

            // Calibrated clinical ECG sweep speed (~1 cycle per ~0.85 sec at 72 bpm)
            float speed = (bpm / 60.0f) * (dt / 1000.0f) * 0.95f;
            phase += speed;

            // Push simulated physiological sample
            sampleBuffer.addLast(getEcgValue(phase));
            if (sampleBuffer.size() > MAX_SAMPLES) {
                sampleBuffer.removeFirst();
            }

            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        isRunning = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    /**
     * Physiological ECG waveform generator (P-Q-R-S-T sequence)
     * Output normalized roughly between -1.0 and 1.0
     */
    private float getEcgValue(float p) {
        float t = p % 1.0f; // cycle from 0 to 1

        if (t < 0.15f) {
            // Baseline
            return 0f;
        } else if (t < 0.25f) {
            // P-wave (atrial depolarization)
            float local = (t - 0.15f) / 0.10f;
            return (float) (0.22f * Math.sin(local * Math.PI));
        } else if (t < 0.35f) {
            // PR segment
            return 0f;
        } else if (t < 0.38f) {
            // Q-wave (small downward dip)
            float local = (t - 0.35f) / 0.03f;
            return -0.15f * local;
        } else if (t < 0.44f) {
            // R-wave (sharp tall upward spike)
            float local = (t - 0.38f) / 0.06f;
            if (local < 0.5f) {
                return -0.15f + (1.15f * (local / 0.5f));
            } else {
                return 1.0f - (1.35f * ((local - 0.5f) / 0.5f));
            }
        } else if (t < 0.48f) {
            // S-wave (downward valley)
            float local = (t - 0.44f) / 0.04f;
            return -0.35f * (1f - local);
        } else if (t < 0.58f) {
            // ST segment (isoelectric baseline)
            return 0f;
        } else if (t < 0.74f) {
            // T-wave (ventricular repolarization)
            float local = (t - 0.58f) / 0.16f;
            return (float) (0.35f * Math.sin(local * Math.PI));
        } else {
            // TP baseline
            return 0f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        // 1. Draw Medical Grid
        float gridSpacing = height / 5.0f;

        // Minor horizontal grid lines
        for (float y = 0; y <= height; y += gridSpacing / 2f) {
            canvas.drawLine(0, y, width, y, gridPaintMinor);
        }
        // Major horizontal grid lines
        for (float y = 0; y <= height; y += gridSpacing) {
            canvas.drawLine(0, y, width, y, gridPaintMajor);
        }

        // Minor vertical grid lines
        for (float x = 0; x <= width; x += gridSpacing / 2f) {
            canvas.drawLine(x, 0, x, height, gridPaintMinor);
        }
        // Major vertical grid lines
        for (float x = 0; x <= width; x += gridSpacing) {
            canvas.drawLine(x, 0, x, height, gridPaintMajor);
        }

        // 2. Draw ECG Waveform
        ecgPath.reset();
        int size = sampleBuffer.size();
        if (size < 2) return;

        float centerY = height * 0.52f;
        float scaleY = height * 0.40f;
        float stepX = (float) width / (MAX_SAMPLES - 1);

        int index = 0;
        float lastX = 0, lastY = centerY;

        for (Float sample : sampleBuffer) {
            float x = index * stepX;
            float y = centerY - (sample * scaleY);

            if (index == 0) {
                ecgPath.moveTo(x, y);
            } else {
                ecgPath.lineTo(x, y);
            }

            lastX = x;
            lastY = y;
            index++;
        }

        // Draw glow and main line
        canvas.drawPath(ecgPath, ecgGlowPaint);
        canvas.drawPath(ecgPath, ecgLinePaint);

        // Draw sweeping head dot
        if (size > 0) {
            canvas.drawCircle(lastX, lastY, 4.5f, sweepDotPaint);
        }
    }
}
