package com.emergency.patient.models;

import android.graphics.Bitmap;
import java.io.Serializable;
import java.util.Locale;

/**
 * LesionMaskData — Holds segmentation mask information, display state,
 * and metadata for a single retinal lesion class.
 */
public class LesionMaskData implements Serializable {

    private final LesionCategory category;
    private boolean visible = true;
    private boolean available = false;
    private String statusDescription = "Pending Analysis";
    private String maskFilePath = null;
    private Integer regionCount = null;
    private Long pixelArea = null;

    // Bitmap is transient because it cannot be serialized directly across intents/bundles
    private transient Bitmap maskBitmap = null;

    public LesionMaskData(LesionCategory category) {
        this.category = category;
    }

    public LesionCategory getCategory() {
        return category;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    public String getMaskFilePath() {
        return maskFilePath;
    }

    public void setMaskFilePath(String maskFilePath) {
        this.maskFilePath = maskFilePath;
    }

    public Integer getRegionCount() {
        return regionCount;
    }

    public void setRegionCount(Integer regionCount) {
        this.regionCount = regionCount;
    }

    public Long getPixelArea() {
        return pixelArea;
    }

    public void setPixelArea(Long pixelArea) {
        this.pixelArea = pixelArea;
    }

    public Bitmap getMaskBitmap() {
        return maskBitmap;
    }

    public void setMaskBitmap(Bitmap maskBitmap) {
        this.maskBitmap = maskBitmap;
    }

    /**
     * Formats pixel coverage information without inventing physical retinal measurements.
     */
    public String getFormattedPixelArea() {
        if (pixelArea == null || pixelArea <= 0) {
            return "Not available";
        }
        return String.format(Locale.getDefault(), "%,d pixels", pixelArea);
    }

    /**
     * Formats region/cluster count.
     */
    public String getFormattedRegionCount() {
        if (regionCount == null || regionCount < 0) {
            return "Not available";
        }
        return regionCount + (regionCount == 1 ? " region" : " regions");
    }
}

