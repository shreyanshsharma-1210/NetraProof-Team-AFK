package com.emergency.patient.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RainDropletsAnimationView — Lightweight Canvas-rendered rain particle simulation.
 * Features:
 *  - Angled falling raindrops with velocity variations
 *  - Radial splash ripple effects when hitting the ground
 *  - Subtle atmospheric mist / droplet glow
 */
public class RainDropletsAnimationView extends View {

    private static final int DROPLET_COUNT = 80;
    private final List<Droplet> droplets = new ArrayList<>();
    private final List<Splash> splashes = new ArrayList<>();
    private final Paint dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint splashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private ValueAnimator animator;
    private boolean isAnimating = false;

    public RainDropletsAnimationView(Context context) {
        super(context);
        init();
    }

    public RainDropletsAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RainDropletsAnimationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dropPaint.setStyle(Paint.Style.STROKE);
        dropPaint.setStrokeCap(Paint.Cap.ROUND);

        splashPaint.setStyle(Paint.Style.STROKE);
        splashPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initDroplets(w, h);
        startAnimation();
    }

    private void initDroplets(int w, int h) {
        droplets.clear();
        splashes.clear();
        if (w <= 0 || h <= 0) return;

        for (int i = 0; i < DROPLET_COUNT; i++) {
            Droplet d = new Droplet();
            d.x = random.nextFloat() * w;
            d.y = random.nextFloat() * h;
            d.length = 28f + random.nextFloat() * 36f;
            d.speed = 18f + random.nextFloat() * 22f;
            d.strokeWidth = 2.8f + random.nextFloat() * 2.2f;
            d.slant = 3f + random.nextFloat() * 5f; // Wind angle
            d.alpha = 180 + random.nextInt(75);
            // Color variation between vibrant sky blue (#38BDF8), electric cyan (#00E5FF), and pure white
            int colorType = random.nextInt(3);
            if (colorType == 0) {
                d.r = 56; d.g = 189; d.b = 248; // #38BDF8
            } else if (colorType == 1) {
                d.r = 0; d.g = 229; d.b = 255;  // #00E5FF
            } else {
                d.r = 255; d.g = 255; d.b = 255; // White
            }
            droplets.add(d);
        }
    }

    public void startAnimation() {
        if (isAnimating) return;
        isAnimating = true;

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            updatePhysics();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        isAnimating = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void updatePhysics() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Update Droplets
        for (Droplet d : droplets) {
            d.y += d.speed;
            d.x += (d.slant * 0.5f);

            if (d.y > h - 8) {
                // Trigger splash ripple at ground
                if (random.nextFloat() < 0.5f && splashes.size() < 20) {
                    Splash s = new Splash();
                    s.x = d.x;
                    s.y = h - 4f;
                    s.radius = 2f;
                    s.maxRadius = 12f + random.nextFloat() * 18f;
                    s.alpha = 230;
                    splashes.add(s);
                }
                // Reset droplet back to top
                d.y = -d.length - (random.nextFloat() * 40f);
                d.x = random.nextFloat() * (w + 40) - 20;
            }
        }

        // Update Splashes
        for (int i = splashes.size() - 1; i >= 0; i--) {
            Splash s = splashes.get(i);
            s.radius += 1.6f;
            s.alpha -= 12;
            if (s.alpha <= 0 || s.radius >= s.maxRadius) {
                splashes.remove(i);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw Droplets
        for (Droplet d : droplets) {
            dropPaint.setColor(Color.argb(d.alpha, d.r, d.g, d.b));
            dropPaint.setStrokeWidth(d.strokeWidth);
            canvas.drawLine(d.x, d.y, d.x + d.slant, d.y + d.length, dropPaint);
        }

        // Draw Splashes
        for (Splash s : splashes) {
            splashPaint.setColor(Color.argb(Math.max(0, s.alpha), 56, 189, 248)); // #38BDF8
            splashPaint.setStrokeWidth(2.2f);
            canvas.drawCircle(s.x, s.y, s.radius, splashPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private static class Droplet {
        float x, y;
        float length;
        float speed;
        float strokeWidth;
        float slant;
        int alpha;
        int r, g, b;
    }

    private static class Splash {
        float x, y;
        float radius;
        float maxRadius;
        int alpha;
    }
}
