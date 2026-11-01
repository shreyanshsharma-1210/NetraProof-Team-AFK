package com.emergency.patient.models;

import androidx.annotation.ColorRes;
import com.emergency.patient.R;

import java.io.Serializable;

/**
 * LesionCategory — Represents the four distinct retinal lesion categories segmented
 * by the NetraProof U-Net + SE-ResNeXt50 architecture.
 *
 * Visual Palette Standards:
 *  - Cotton Wool Spots: Blue (#2979FF)
 *  - Hard Exudates: Yellow (#FBC02D)
 *  - Hemorrhages: Red (#E53935)
 *  - Microaneurysms: Magenta (#E040FB)
 *
 * Clinical Distinction:
 *  - Colors are strictly visualization conventions to differentiate morphology,
 *    never representations of clinical severity or prognosis.
 */
public enum LesionCategory implements Serializable {

    COTTON_WOOL_SPOTS(
            "Cotton Wool Spots",
            "CWS",
            "#2979FF",
            R.color.lesion_cotton_wool,
            R.color.lesion_cotton_wool_tint,
            R.drawable.bg_lesion_indicator_cws,
            "Cotton wool spots are retinal findings associated with localized areas of retinal nerve fiber layer changes.",
            "Characterized by fluffy, superficial white/grey retinal opacities caused by localized axoplasmic flow blockage in retinal ganglion cell axons."
    ),

    HARD_EXUDATES(
            "Hard Exudates",
            "HEX",
            "#FBC02D",
            R.color.lesion_hard_exudates,
            R.color.lesion_hard_exudates_tint,
            R.drawable.bg_lesion_indicator_hex,
            "Hard exudates are lipid-rich deposits that may appear as bright retinal lesions.",
            "Waxy, well-defined yellow-white deposits formed when lipoprotein complexes leak from incompetent retinal capillaries into the outer plexiform layer."
    ),

    HEMORRHAGES(
            "Hemorrhages",
            "HEM",
            "#E53935",
            R.color.lesion_hemorrhages,
            R.color.lesion_hemorrhages_tint,
            R.drawable.bg_lesion_indicator_hem,
            "Retinal hemorrhages are areas of bleeding within retinal tissue.",
            "Dot/blot hemorrhages occur in the deeper retinal layers (inner nuclear/outer plexiform), while flame hemorrhages occur in the superficial nerve fiber layer."
    ),

    MICROANEURYSMS(
            "Microaneurysms",
            "MA",
            "#E040FB",
            R.color.lesion_microaneurysms,
            R.color.lesion_microaneurysms_tint,
            R.drawable.bg_lesion_indicator_ma,
            "Microaneurysms are small focal dilations of retinal capillaries.",
            "Often the earliest clinically observable microvascular sign of diabetic retinopathy, appearing as tiny round red dots with well-defined margins."
    );

    private final String displayName;
    private final String shortCode;
    private final String colorHex;
    private final int colorResId;
    private final int tintResId;
    private final int indicatorDrawableResId;
    private final String clinicalDescription;
    private final String educationalNotes;

    LesionCategory(
            String displayName,
            String shortCode,
            String colorHex,
            @ColorRes int colorResId,
            @ColorRes int tintResId,
            int indicatorDrawableResId,
            String clinicalDescription,
            String educationalNotes
    ) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.colorHex = colorHex;
        this.colorResId = colorResId;
        this.tintResId = tintResId;
        this.indicatorDrawableResId = indicatorDrawableResId;
        this.clinicalDescription = clinicalDescription;
        this.educationalNotes = educationalNotes;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getColorHex() {
        return colorHex;
    }

    public int getColorResId() {
        return colorResId;
    }

    public int getTintResId() {
        return tintResId;
    }

    public int getIndicatorDrawableResId() {
        return indicatorDrawableResId;
    }

    public String getClinicalDescription() {
        return clinicalDescription;
    }

    public String getEducationalNotes() {
        return educationalNotes;
    }
}

