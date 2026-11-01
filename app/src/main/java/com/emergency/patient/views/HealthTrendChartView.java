package com.emergency.patient.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HealthTrendChartView — Lightweight, high-precision canvas chart for physiological biomarkers.
 *
 * Renders anti-aliased trend curves for:
 *  - Single metrics: eGFR, Serum Creatinine, UACR, Lipids, Glucose
 *  - Dual metrics: Systolic & Diastolic Blood Pressure
 *
 * Implements empty-state canvas fallback and strict date/unit provenance display.
 */
public class HealthTrendChartView extends View {

    public static class DataPoint {
        public long timestampMillis;
        public double primaryValue;
        public Double secondaryValue; // Optional for dual-series (e.g. diastolic BP)
        public String label;

        public DataPoint(long timestampMillis, double primaryValue, @Nullable Double secondaryValue, String label) {
            this.timestampMillis = timestampMillis;
            this.primaryValue = primaryValue;
            this.secondaryValue = secondaryValue;
            this.label = label;
        }
    }

    private final List<DataPoint> dataPoints = new ArrayList<>();
    private String unitLabel = "";
    private String primarySeriesName = "Primary";
    private String secondarySeriesName = "Secondary";
    private boolean isDualSeries = false;

    // Paints
    private Paint gridPaint;
    private Paint textPaint;
    private Paint linePrimaryPaint;
    private Paint fillPrimaryPaint;
    private Paint dotPrimaryPaint;
    private Paint lineSecondaryPaint;
    private Paint dotSecondaryPaint;
    private Paint emptyTextPaint;

    // Colors
    private final int colorPrimary = Color.parseColor("#147D82");
    private final int colorSecondary = Color.parseColor("#E67E22");
    private final int colorGrid = Color.parseColor("#E4EAEE");
    private final int colorText = Color.parseColor("#71818D");

    public HealthTrendChartView(Context context) {
        super(context);
        init();
    }

    public HealthTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HealthTrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(colorGrid);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(colorText);
        textPaint.setTextSize(spToPx(10));

        emptyTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyTextPaint.setColor(colorText);
        emptyTextPaint.setTextSize(spToPx(12));
        emptyTextPaint.setTextAlign(Paint.Align.CENTER);

        linePrimaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePrimaryPaint.setColor(colorPrimary);
        linePrimaryPaint.setStrokeWidth(6f);
        linePrimaryPaint.setStyle(Paint.Style.STROKE);
        linePrimaryPaint.setStrokeCap(Paint.Cap.ROUND);
        linePrimaryPaint.setPathEffect(new CornerPathEffect(16f));

        fillPrimaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPrimaryPaint.setStyle(Paint.Style.FILL);

        dotPrimaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPrimaryPaint.setColor(colorPrimary);
        dotPrimaryPaint.setStyle(Paint.Style.FILL);

        lineSecondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineSecondaryPaint.setColor(colorSecondary);
        lineSecondaryPaint.setStrokeWidth(5f);
        lineSecondaryPaint.setStyle(Paint.Style.STROKE);
        lineSecondaryPaint.setStrokeCap(Paint.Cap.ROUND);
        lineSecondaryPaint.setPathEffect(new CornerPathEffect(16f));

        dotSecondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotSecondaryPaint.setColor(colorSecondary);
        dotSecondaryPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<DataPoint> points, String unit, boolean dualSeries, String primaryName, String secondaryName) {
        this.dataPoints.clear();
        if (points != null) {
            this.dataPoints.addAll(points);
            Collections.sort(this.dataPoints, (a, b) -> Long.compare(a.timestampMillis, b.timestampMillis));
        }
        this.unitLabel = unit != null ? unit : "";
        this.isDualSeries = dualSeries;
        this.primarySeriesName = primaryName;
        this.secondarySeriesName = secondaryName;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        if (w <= 0 || h <= 0) return;

        if (dataPoints.isEmpty()) {
            drawEmptyState(canvas, w, h);
            return;
        }

        float padLeft = dpToPx(38);
        float padRight = dpToPx(16);
        float padTop = dpToPx(24);
        float padBottom = dpToPx(32);

        float chartW = w - padLeft - padRight;
        float chartH = h - padTop - padBottom;

        // Calculate Min and Max
        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;

        for (DataPoint dp : dataPoints) {
            minVal = Math.min(minVal, dp.primaryValue);
            maxVal = Math.max(maxVal, dp.primaryValue);
            if (isDualSeries && dp.secondaryValue != null) {
                minVal = Math.min(minVal, dp.secondaryValue);
                maxVal = Math.max(maxVal, dp.secondaryValue);
            }
        }

        if (minVal == maxVal) {
            minVal = Math.max(0, minVal - 10);
            maxVal = maxVal + 10;
        } else {
            double range = maxVal - minVal;
            minVal = Math.max(0, minVal - range * 0.15);
            maxVal = maxVal + range * 0.15;
        }

        // Draw 3 horizontal grid lines (top, middle, bottom)
        for (int i = 0; i <= 2; i++) {
            float y = padTop + chartH * (i / 2f);
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint);

            double guideVal = maxVal - (maxVal - minVal) * (i / 2f);
            String valStr = String.format(Locale.getDefault(), "%.0f", guideVal);
            canvas.drawText(valStr, dpToPx(4), y + dpToPx(4), textPaint);
        }

        // Draw Unit at top left
        if (!unitLabel.isEmpty()) {
            canvas.drawText(unitLabel, padLeft, padTop - dpToPx(8), textPaint);
        }

        // Single Point Case
        if (dataPoints.size() == 1) {
            DataPoint single = dataPoints.get(0);
            float cx = padLeft + chartW / 2f;
            float cy = (float) (padTop + chartH * (1.0 - (single.primaryValue - minVal) / (maxVal - minVal)));
            canvas.drawCircle(cx, cy, dpToPx(6), dotPrimaryPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", single.primaryValue), cx - dpToPx(12), cy - dpToPx(10), textPaint);

            String dateStr = formatDate(single.timestampMillis);
            canvas.drawText(dateStr, cx - dpToPx(18), h - dpToPx(8), textPaint);
            return;
        }

        // Multiple Points: Construct Path
        Path pathPrimary = new Path();
        Path fillPath = new Path();
        Path pathSecondary = isDualSeries ? new Path() : null;

        List<Float> xCoords = new ArrayList<>();
        List<Float> yCoordsPrimary = new ArrayList<>();
        List<Float> yCoordsSecondary = new ArrayList<>();

        for (int i = 0; i < dataPoints.size(); i++) {
            DataPoint pt = dataPoints.get(i);
            float x = padLeft + (chartW * i / (dataPoints.size() - 1f));
            float y1 = (float) (padTop + chartH * (1.0 - (pt.primaryValue - minVal) / (maxVal - minVal)));

            xCoords.add(x);
            yCoordsPrimary.add(y1);

            if (i == 0) {
                pathPrimary.moveTo(x, y1);
                fillPath.moveTo(x, padTop + chartH);
                fillPath.lineTo(x, y1);
            } else {
                pathPrimary.lineTo(x, y1);
                fillPath.lineTo(x, y1);
            }

            if (isDualSeries && pt.secondaryValue != null) {
                float y2 = (float) (padTop + chartH * (1.0 - (pt.secondaryValue - minVal) / (maxVal - minVal)));
                yCoordsSecondary.add(y2);
                if (i == 0) {
                    pathSecondary.moveTo(x, y2);
                } else {
                    pathSecondary.lineTo(x, y2);
                }
            }
        }

        fillPath.lineTo(xCoords.get(xCoords.size() - 1), padTop + chartH);
        fillPath.close();

        // Gradient under primary line
        fillPrimaryPaint.setShader(new LinearGradient(0, padTop, 0, padTop + chartH,
                Color.argb(45, 20, 125, 130), Color.argb(0, 20, 125, 130), Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPrimaryPaint);

        // Draw primary line
        canvas.drawPath(pathPrimary, linePrimaryPaint);

        // Draw secondary line (if dual)
        if (isDualSeries && pathSecondary != null) {
            canvas.drawPath(pathSecondary, lineSecondaryPaint);
        }

        // Draw Data dots and X labels
        for (int i = 0; i < dataPoints.size(); i++) {
            float x = xCoords.get(i);
            float y1 = yCoordsPrimary.get(i);
            canvas.drawCircle(x, y1, dpToPx(4.5f), dotPrimaryPaint);

            if (isDualSeries && i < yCoordsSecondary.size()) {
                float y2 = yCoordsSecondary.get(i);
                canvas.drawCircle(x, y2, dpToPx(4f), dotSecondaryPaint);
            }

            // Draw date on first, middle, last points to avoid clutter
            if (i == 0 || i == dataPoints.size() - 1 || (dataPoints.size() > 4 && i == dataPoints.size() / 2)) {
                String dStr = formatDate(dataPoints.get(i).timestampMillis);
                float textW = textPaint.measureText(dStr);
                float textX = Math.max(padLeft, Math.min(x - textW / 2f, w - padRight - textW));
                canvas.drawText(dStr, textX, h - dpToPx(8), textPaint);
            }
        }
    }

    private void drawEmptyState(Canvas canvas, int w, int h) {
        float midY = h / 2f;
        canvas.drawText("No historical measurements recorded yet", w / 2f, midY, emptyTextPaint);
        Paint hintPaint = new Paint(emptyTextPaint);
        hintPaint.setTextSize(spToPx(10));
        hintPaint.setColor(Color.parseColor("#9AA6B2"));
        canvas.drawText("Add records using the button above to view physiological trends.", w / 2f, midY + dpToPx(18), hintPaint);
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "--";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}

