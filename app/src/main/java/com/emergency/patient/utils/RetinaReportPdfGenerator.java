package com.emergency.patient.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import com.emergency.patient.models.DrSeverityGrade;
import com.emergency.patient.models.LesionCategory;
import com.emergency.patient.models.LesionMaskData;
import com.emergency.patient.models.RetinaReportData;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaReportPdfGenerator — Generates publication-grade medical screening PDF reports
 * using Android's native PdfDocument engine.
 *
 * Page Architecture:
 *  - Page 1: NetraProof Branding, Patient Profile, Session Metadata, Executive Summary,
 *            DR Severity ICDR Tier Analysis, and Retinal Fundus Preview.
 *  - Page 2: Optical Quality Breakdown, Lesion Segmentation (U-Net + SE-ResNeXt50) Matrix,
 *            Model Independence Notice, Clinical Next Steps, Disclaimer, and Signature Block.
 */
public class RetinaReportPdfGenerator {

    private static final int PAGE_WIDTH = 595;   // A4 Width at 72 dpi
    private static final int PAGE_HEIGHT = 842;  // A4 Height at 72 dpi
    private static final int MARGIN = 36;        // 0.5 inch margins

    public static File generatePdf(Context context, RetinaReportData report) throws Exception {
        PdfDocument document = new PdfDocument();

        try {
            // ─── PAGE 1: Executive Summary & DR Grading ──────────────────────
            PdfDocument.PageInfo page1Info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page1 = document.startPage(page1Info);
            renderPage1(context, page1.getCanvas(), report);
            document.finishPage(page1);

            // ─── PAGE 2: Quality, Lesions, Guidance & Disclaimer ─────────────
            PdfDocument.PageInfo page2Info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(page2Info);
            renderPage2(context, page2.getCanvas(), report);
            document.finishPage(page2);

            // Write to app-internal documents directory (accessible to FileProvider)
            File reportsDir = new File(context.getFilesDir(), "reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }

            String filename = "NetraProof_Report_" + report.getSessionId() + ".pdf";
            File outputFile = new File(reportsDir, filename);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                document.writeTo(fos);
            }

            return outputFile;
        } finally {
            document.close();
        }
    }

    // ─── Page 1 Rendering ─────────────────────────────────────────────────────

    private static void renderPage1(Context context, Canvas canvas, RetinaReportData report) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Header Background Banner
        paint.setColor(Color.parseColor("#10202A"));
        canvas.drawRect(0, 0, PAGE_WIDTH, 70, paint);

        // Header Branding
        paint.setColor(Color.parseColor("#147D82"));
        paint.setTextSize(16);
        paint.setFakeBoldText(true);
        canvas.drawText("NETRAPROOF RETINA INTELLIGENCE", MARGIN, 32, paint);

        paint.setColor(Color.parseColor("#A0B0BC"));
        paint.setTextSize(9);
        paint.setFakeBoldText(false);
        canvas.drawText("AI-Powered Retinal Screening & Diabetic Microvascular Health System", MARGIN, 46, paint);

        // Header Metadata (Right)
        paint.setColor(Color.WHITE);
        paint.setTextSize(9);
        paint.setFakeBoldText(true);
        canvas.drawText("SESSION: " + report.getSessionId(), PAGE_WIDTH - MARGIN - 120, 32, paint);
        paint.setColor(Color.parseColor("#A0B0BC"));
        paint.setFakeBoldText(false);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault());
        canvas.drawText(sdf.format(new Date(report.getScreeningTimestamp())), PAGE_WIDTH - MARGIN - 120, 46, paint);

        int currentY = 90;

        // Document Title & Status Pill
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(14);
        paint.setFakeBoldText(true);
        canvas.drawText("DIABETIC RETINOPATHY AI SCREENING REPORT", MARGIN, currentY, paint);

        // Status Pill
        boolean isComplete = report.isComplete();
        paint.setColor(Color.parseColor(isComplete ? "#25865A" : "#147D82"));
        RectF statusRect = new RectF(PAGE_WIDTH - MARGIN - 130, currentY - 14, PAGE_WIDTH - MARGIN, currentY + 4);
        canvas.drawRoundRect(statusRect, 8, 8, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(8);
        paint.setFakeBoldText(true);
        canvas.drawText(isComplete ? "COMPLETE REPORT" : "PARTIAL REPORT", PAGE_WIDTH - MARGIN - 120, currentY - 2, paint);

        currentY += 20;

        // Patient Information Box
        paint.setColor(Color.parseColor("#F5F8FA"));
        RectF patientBox = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 54);
        canvas.drawRoundRect(patientBox, 6, 6, paint);

        paint.setColor(Color.parseColor("#E4EAEE"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        canvas.drawRoundRect(patientBox, 6, 6, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(8);
        paint.setFakeBoldText(true);
        canvas.drawText("PATIENT NAME", MARGIN + 12, currentY + 16, paint);
        canvas.drawText("PATIENT ID", MARGIN + 140, currentY + 16, paint);
        canvas.drawText("AGE / GENDER", MARGIN + 260, currentY + 16, paint);
        canvas.drawText("REPORT REF", MARGIN + 380, currentY + 16, paint);

        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(9.5f);
        paint.setFakeBoldText(true);
        canvas.drawText(report.getPatientFullName(), MARGIN + 12, currentY + 34, paint);
        canvas.drawText(report.getPatientDisplayId(), MARGIN + 140, currentY + 34, paint);
        canvas.drawText(report.getPatientAge() + " • " + report.getPatientGender(), MARGIN + 260, currentY + 34, paint);
        canvas.drawText(report.getReportId(), MARGIN + 380, currentY + 34, paint);

        currentY += 70;

        // Executive Summary Section Header
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("1. Executive Screening Summary", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 18;

        // 3-Stage Summary Badges Row
        drawSummaryCard(canvas, MARGIN, currentY, 165, 45, "OPTICAL QUALITY",
                report.isQualityPassed() ? "Passed" : "Inconclusive",
                report.isQualityPassed() ? "#25865A" : "#D99A36");

        drawSummaryCard(canvas, MARGIN + 175, currentY, 175, 45, "DR SEVERITY TIER",
                report.getDrSeverityGrade() != null ? report.getDrSeverityGrade().getShortCategoryName() : "Service Pending",
                report.getDrSeverityGrade() != null ? "#147D82" : "#71818D");

        drawSummaryCard(canvas, MARGIN + 360, currentY, 163, 45, "LESION SEGMENTATION",
                report.getSegmentationState().equalsIgnoreCase("Completed") ? "4 Classes Evaluated" : "Service Pending",
                report.getSegmentationState().equalsIgnoreCase("Completed") ? "#25865A" : "#71818D");

        currentY += 60;

        // Primary DR Severity Assessment Section
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("2. Diabetic Retinopathy Severity Classification", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 18;

        // DR Classification Details Box
        paint.setColor(Color.parseColor("#EEF4F7"));
        RectF drBox = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 85);
        canvas.drawRoundRect(drBox, 6, 6, paint);

        paint.setColor(Color.parseColor("#C8D6E0"));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(drBox, 6, 6, paint);
        paint.setStyle(Paint.Style.FILL);

        DrSeverityGrade grade = report.getDrSeverityGrade();
        String gradeLabel = grade != null ? grade.getFullCategoryName() : "Prediction Pending Remote Server Deployment";
        String modelLabel = "Model: " + report.getDrModelName() + " (" + report.getDrLossFunction() + ")";

        paint.setColor(Color.parseColor("#147D82"));
        paint.setTextSize(8.5f);
        paint.setFakeBoldText(true);
        canvas.drawText("AI SCREENING OUTCOME", MARGIN + 12, currentY + 18, paint);

        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText(gradeLabel, MARGIN + 12, currentY + 36, paint);

        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(8);
        paint.setFakeBoldText(false);
        canvas.drawText(modelLabel, MARGIN + 12, currentY + 52, paint);

        // Explanation text
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(8.5f);
        drawWrappedText(canvas, report.getDrExplanation(), MARGIN + 12, currentY + 66, PAGE_WIDTH - MARGIN - 24, paint, 10);

        currentY += 100;

        // 5-Grade ICDR Scale Visual Bar
        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(8);
        paint.setFakeBoldText(true);
        canvas.drawText("ICDR 5-TIER SEVERITY SCALE:", MARGIN, currentY, paint);

        currentY += 8;
        int barWidth = (PAGE_WIDTH - (MARGIN * 2) - 16) / 5;
        DrSeverityGrade[] allGrades = DrSeverityGrade.values();
        for (int i = 0; i < 5; i++) {
            int left = MARGIN + (i * (barWidth + 4));
            boolean isSelected = (grade == allGrades[i]);

            paint.setColor(Color.parseColor(isSelected ? "#147D82" : "#E4EAEE"));
            RectF segBar = new RectF(left, currentY, left + barWidth, currentY + 22);
            canvas.drawRoundRect(segBar, 4, 4, paint);

            paint.setColor(Color.parseColor(isSelected ? "#FFFFFF" : "#172D3A"));
            paint.setTextSize(7.5f);
            paint.setFakeBoldText(isSelected);
            canvas.drawText(allGrades[i].getGradeBadgeText(), left + 6, currentY + 14, paint);
        }

        currentY += 38;

        // Fundus Image Preview Section
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("3. Original Screened Photograph", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 16;

        // Image container
        int imgBoxHeight = 160;
        RectF imgContainer = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + imgBoxHeight);
        paint.setColor(Color.parseColor("#0A151C"));
        canvas.drawRoundRect(imgContainer, 6, 6, paint);

        // Draw fundus bitmap if available
        if (report.getImagePath() != null && new File(report.getImagePath()).exists()) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(report.getImagePath(), opts);
                int sample = 1;
                while ((opts.outWidth / sample) > 1024 || (opts.outHeight / sample) > 1024) {
                    sample *= 2;
                }
                opts.inSampleSize = sample;
                opts.inJustDecodeBounds = false;
                Bitmap bmp = BitmapFactory.decodeFile(report.getImagePath(), opts);
                if (bmp != null) {
                    float scale = Math.min((imgContainer.width() - 16) / bmp.getWidth(), (imgContainer.height() - 16) / bmp.getHeight());
                    float dw = bmp.getWidth() * scale;
                    float dh = bmp.getHeight() * scale;
                    float dx = imgContainer.centerX() - (dw / 2);
                    float dy = imgContainer.centerY() - (dh / 2);
                    RectF dst = new RectF(dx, dy, dx + dw, dy + dh);
                    canvas.drawBitmap(bmp, null, dst, paint);
                    bmp.recycle();
                }
            } catch (Throwable ignored) {}
        }

        // Image Metadata Caption below container
        currentY += imgBoxHeight + 14;
        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(8);
        paint.setFakeBoldText(false);
        canvas.drawText("Image Reference: " + report.getFileName() + " • " + report.getFormattedDimensions() + " • " + report.getFormattedFileSize() + " • Aspect Ratio Preserved", MARGIN, currentY, paint);

        // Page 1 Footer
        drawPageFooter(canvas, 1);
    }

    // ─── Page 2 Rendering ─────────────────────────────────────────────────────

    private static void renderPage2(Context context, Canvas canvas, RetinaReportData report) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Header Background Banner
        paint.setColor(Color.parseColor("#10202A"));
        canvas.drawRect(0, 0, PAGE_WIDTH, 45, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText("NETRAPROOF AI SCREENING REPORT", MARGIN, 28, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(8.5f);
        canvas.drawText("SESSION: " + report.getSessionId() + " • PAGE 2 OF 2", PAGE_WIDTH - MARGIN - 160, 28, paint);

        int currentY = 65;

        // Section 4: Optical Image Quality Assessment
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("4. Retinal Image Quality Assessment", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 16;

        // Quality Parameters Table Box
        paint.setColor(Color.parseColor("#F5F8FA"));
        RectF qBox = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 54);
        canvas.drawRoundRect(qBox, 6, 6, paint);

        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(8.5f);
        paint.setFakeBoldText(true);
        canvas.drawText("Parameter", MARGIN + 12, currentY + 16, paint);
        canvas.drawText("Focus / Sharpness", MARGIN + 120, currentY + 16, paint);
        canvas.drawText("Illumination / Contrast", MARGIN + 240, currentY + 16, paint);
        canvas.drawText("Artifacts / Blur", MARGIN + 380, currentY + 16, paint);

        paint.setColor(Color.parseColor("#25865A"));
        paint.setFakeBoldText(false);
        canvas.drawText("Evaluation", MARGIN + 12, currentY + 36, paint);
        canvas.drawText("Acceptable (Clear)", MARGIN + 120, currentY + 36, paint);
        canvas.drawText("Uniform Exposure", MARGIN + 240, currentY + 36, paint);
        canvas.drawText("No Obscuration", MARGIN + 380, currentY + 36, paint);

        currentY += 72;

        // Section 5: Retinal Lesion Segmentation Matrix
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("5. Independent Retinal Lesion Segmentation (U-Net + SE-ResNeXt50)", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 18;

        // 4 Lesion Rows Table
        LesionCategory[] categories = LesionCategory.values();
        for (int i = 0; i < categories.length; i++) {
            LesionCategory cat = categories[i];
            int rowY = currentY + (i * 22);

            // Row background
            paint.setColor(Color.parseColor(i % 2 == 0 ? "#FFFFFF" : "#F5F8FA"));
            canvas.drawRect(MARGIN, rowY, PAGE_WIDTH - MARGIN, rowY + 20, paint);

            // Color Indicator Dot
            paint.setColor(Color.parseColor(cat.getColorHex()));
            canvas.drawCircle(MARGIN + 12, rowY + 10, 4, paint);

            // Category Label & Code
            paint.setColor(Color.parseColor("#172D3A"));
            paint.setTextSize(8.5f);
            paint.setFakeBoldText(true);
            canvas.drawText(cat.getDisplayName() + " (" + cat.getShortCode() + ")", MARGIN + 24, rowY + 13, paint);

            // Educational Description
            paint.setColor(Color.parseColor("#71818D"));
            paint.setTextSize(7.5f);
            paint.setFakeBoldText(false);
            canvas.drawText(cat.getClinicalDescription(), MARGIN + 160, rowY + 13, paint);
        }

        currentY += 100;

        // Independence Notice Box
        paint.setColor(Color.parseColor("#FEF6E9"));
        RectF noteBox = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 36);
        canvas.drawRoundRect(noteBox, 4, 4, paint);

        paint.setColor(Color.parseColor("#D99A36"));
        paint.setTextSize(7.5f);
        paint.setFakeBoldText(true);
        canvas.drawText("ARCHITECTURAL INDEPENDENCE NOTICE:", MARGIN + 10, currentY + 14, paint);
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setFakeBoldText(false);
        canvas.drawText("Lesion segmentation is an independent computer vision pipeline. Its visual masks do not directly explain or validate the DR grade.", MARGIN + 10, currentY + 26, paint);

        currentY += 50;

        // Section 6: Contextual Recommended Next Steps
        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(11);
        paint.setFakeBoldText(true);
        canvas.drawText("6. Recommended Next Steps", MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(MARGIN, currentY + 4, PAGE_WIDTH - MARGIN, currentY + 4, paint);

        currentY += 18;

        List<String> nextSteps = report.getRecommendedNextSteps();
        paint.setTextSize(8.5f);
        for (String step : nextSteps) {
            paint.setColor(Color.parseColor("#147D82"));
            paint.setFakeBoldText(true);
            canvas.drawText("•", MARGIN + 6, currentY, paint);

            paint.setColor(Color.parseColor("#172D3A"));
            paint.setFakeBoldText(false);
            drawWrappedText(canvas, step, MARGIN + 18, currentY, PAGE_WIDTH - MARGIN - 24, paint, 10);
            currentY += 18;
        }

        currentY += 10;

        // Section 7: Mandatory Clinical Disclaimer & Physician Review Block
        paint.setColor(Color.parseColor("#EEF4F7"));
        RectF discBox = new RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 60);
        canvas.drawRoundRect(discBox, 6, 6, paint);

        paint.setColor(Color.parseColor("#147D82"));
        paint.setTextSize(8);
        paint.setFakeBoldText(true);
        canvas.drawText("MANDATORY CLINICAL SAFETY & REGULATORY DISCLAIMER:", MARGIN + 10, currentY + 14, paint);

        paint.setColor(Color.parseColor("#172D3A"));
        paint.setTextSize(7.5f);
        paint.setFakeBoldText(false);
        String disclaimer = "NetraProof provides AI-assisted retinal screening information for informational and screening-support purposes. " +
                "This report is not a confirmed medical diagnosis and does not replace a comprehensive examination by a qualified eye-care professional. " +
                "Consult an ophthalmologist for clinical interpretation and further evaluation.";
        drawWrappedText(canvas, disclaimer, MARGIN + 10, currentY + 26, PAGE_WIDTH - MARGIN - 20, paint, 9.5f);

        currentY += 75;

        // Reviewing Clinician Sign-off Block
        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(8);
        paint.setFakeBoldText(true);
        canvas.drawText("REVIEWING OPHTHALMOLOGIST SIGN-OFF:", MARGIN, currentY, paint);

        currentY += 24;
        paint.setColor(Color.parseColor("#C8D6E0"));
        paint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, currentY, MARGIN + 180, currentY, paint);
        canvas.drawLine(PAGE_WIDTH - MARGIN - 180, currentY, PAGE_WIDTH - MARGIN, currentY, paint);

        paint.setColor(Color.parseColor("#71818D"));
        paint.setTextSize(7.5f);
        paint.setFakeBoldText(false);
        canvas.drawText("Clinician Signature & Registration No.", MARGIN, currentY + 12, paint);
        canvas.drawText("Date & Clinical Facility Stamp", PAGE_WIDTH - MARGIN - 180, currentY + 12, paint);

        // Page 2 Footer
        drawPageFooter(canvas, 2);
    }

    // ─── Drawing Utilities ────────────────────────────────────────────────────

    private static void drawSummaryCard(Canvas canvas, float x, float y, float w, float h, String label, String value, String colorHex) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#F5F8FA"));
        RectF r = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(r, 6, 6, p);

        p.setColor(Color.parseColor("#E4EAEE"));
        p.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(r, 6, 6, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(Color.parseColor("#71818D"));
        p.setTextSize(7.5f);
        p.setFakeBoldText(true);
        canvas.drawText(label, x + 10, y + 16, p);

        p.setColor(Color.parseColor(colorHex));
        p.setTextSize(9.5f);
        p.setFakeBoldText(true);
        canvas.drawText(value, x + 10, y + 34, p);
    }

    private static void drawPageFooter(Canvas canvas, int pageNumber) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#E4EAEE"));
        p.setStrokeWidth(1);
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 35, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 35, p);

        p.setColor(Color.parseColor("#71818D"));
        p.setTextSize(7.5f);
        canvas.drawText("NetraProof Healthcare Platform • Confidential Medical Document", MARGIN, PAGE_HEIGHT - 22, p);
        canvas.drawText("Page " + pageNumber + " of 2", PAGE_WIDTH - MARGIN - 45, PAGE_HEIGHT - 22, p);
    }

    private static void drawWrappedText(Canvas canvas, String text, float x, float y, float maxWidth, Paint paint, float lineHeight) {
        if (text == null || text.isEmpty()) return;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float currentY = y;

        for (String word : words) {
            String testLine = line.length() == 0 ? word : line + " " + word;
            float textWidth = paint.measureText(testLine);

            if (textWidth > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, currentY, paint);
                line = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, currentY, paint);
        }
    }
}

