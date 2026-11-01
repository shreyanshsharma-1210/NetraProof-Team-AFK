package com.emergency.patient.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * MiniWaveformView — Compact trend visualizer for Vital Cards
 * Supports waveform lines and vertical bar charts.
 */
public class MiniWaveformView extends View {

    public static final int TYPE_WAVE = 0;
    public static final int TYPE_BARS = 1;

    private int chartType = TYPE_WAVE;
    private int primaryColor = Color.parseColor("#149B66");
    private Paint linePaint;
    private Paint barPaint;
    private Path path = new Path();

    // Default sample points for wave
    private float[] wavePoints = new float[]{0.3f, 0.4f, 0.35f, 0.65f, 0.15f, 0.85f, 0.2f, 0.4f, 0.45f, 0.4f, 0.5f};
    // Default sample bars
    private float[] barValues = new float[]{0.3f, 0.55f, 0.4f, 0.8f, 0.65f, 0.9f, 0.75f, 0.95f};

    public MiniWaveformView(Context context) {
        super(context);
        init(null);
    }

    public MiniWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MiniWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(primaryColor);
        linePaint.setStrokeWidth(2.5f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setPathEffect(new CornerPathEffect(4f));

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(primaryColor);
        barPaint.setStyle(Paint.Style.FILL);
    }

    public void setChartType(int type) {
        this.chartType = type;
        invalidate();
    }

    public void setPrimaryColor(int color) {
        this.primaryColor = color;
        linePaint.setColor(color);
        barPaint.setColor(color);
        invalidate();
    }

    public void setWavePoints(float[] points) {
        if (points != null && points.length > 0) {
            this.wavePoints = points;
            invalidate();
        }
    }

    public void setBarValues(float[] values) {
        if (values != null && values.length > 0) {
            this.barValues = values;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        if (chartType == TYPE_WAVE) {
            drawWave(canvas, width, height);
        } else {
            drawBars(canvas, width, height);
        }
    }

    private void drawWave(Canvas canvas, int width, int height) {
        path.reset();
        int count = wavePoints.length;
        if (count < 2) return;

        float stepX = (float) width / (count - 1);
        float padY = height * 0.1f;
        float usableH = height - (padY * 2);

        for (int i = 0; i < count; i++) {
            float x = i * stepX;
            float y = height - padY - (wavePoints[i] * usableH);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, linePaint);
    }

    private void drawBars(Canvas canvas, int width, int height) {
        int count = barValues.length;
        if (count == 0) return;

        float totalSpacing = width * 0.35f;
        float barWidth = (width - totalSpacing) / count;
        float spacing = totalSpacing / (count + 1);

        for (int i = 0; i < count; i++) {
            float left = spacing + i * (barWidth + spacing);
            float right = left + barWidth;
            float barH = Math.max(4f, barValues[i] * height * 0.9f);
            float top = height - barH;
            float bottom = height;

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint);
        }
    }
}
