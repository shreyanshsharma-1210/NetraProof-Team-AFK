package com.emergency.patient.models;

import com.emergency.patient.R;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DrSeverityGrade — Standard 5-tier International Clinical Diabetic Retinopathy (ICDR) scale.
 *
 * Model Architecture:
 *  - Five-fold EfficientNet-B0 ensemble with ordinal regression.
 *  - Predicts one of five discrete severity tiers (Grade 0 through Grade 4).
 *
 * Classification Tiers:
 *  - Grade 0: No Apparent Diabetic Retinopathy
 *  - Grade 1: Mild Non-Proliferative Diabetic Retinopathy (Mild NPDR)
 *  - Grade 2: Moderate Non-Proliferative Diabetic Retinopathy (Moderate NPDR)
 *  - Grade 3: Severe Non-Proliferative Diabetic Retinopathy (Severe NPDR)
 *  - Grade 4: Proliferative Diabetic Retinopathy (PDR)
 */
public enum DrSeverityGrade implements Serializable {

    GRADE_0(
            0,
            "Grade 0",
            "No Apparent DR",
            "No Apparent Diabetic Retinopathy",
            "No visible microvascular lesions or hallmark DR features identified in this retinal photograph.",
            "The AI screening model did not identify visible features meeting diabetic retinopathy classification criteria in this image. This screening does not guarantee the complete absence of ocular pathology or systemic microvascular disease.",
            Arrays.asList(
                    "Maintain scheduled annual comprehensive dilated eye evaluations with an ophthalmologist or optometrist.",
                    "Continue routine monitoring of blood glucose (fasting / postprandial), HbA1c, and blood pressure.",
                    "Save this screening record in your NetraProof health profile for longitudinal comparison at your next annual checkup."
            ),
            R.color.netra_success,
            R.color.netra_green_tint,
            R.drawable.bg_pill_status_good
    ),

    GRADE_1(
            1,
            "Grade 1",
            "Mild NPDR",
            "Mild Non-Proliferative Diabetic Retinopathy",
            "Mild microvascular alterations (isolated microaneurysms) consistent with early non-proliferative changes.",
            "This category represents mild microvascular changes associated with non-proliferative diabetic retinopathy. Small balloon-like outpouchings in retinal capillaries (microaneurysms) may be present. While vision is generally unaffected at this stage, it signifies early retinal involvement.",
            Arrays.asList(
                    "Schedule a clinical consultation with an eye-care professional for formal confirmation and baseline assessment.",
                    "Review glycemic targets, lipid profile, and blood pressure control with your primary care physician or diabetologist.",
                    "Plan a repeat retinal screening within 6 to 12 months as advised by your healthcare provider."
            ),
            R.color.netra_primary_teal,
            R.color.netra_teal_light_tint,
            R.drawable.bg_pill_status_good
    ),

    GRADE_2(
            2,
            "Grade 2",
            "Moderate NPDR",
            "Moderate Non-Proliferative Diabetic Retinopathy",
            "Definite non-proliferative changes including microaneurysms, intraretinal hemorrhages, or hard exudates.",
            "This category represents a greater degree of retinal microvascular changes than mild NPDR, but below the threshold for severe NPDR. Features such as scattered dot-and-blot hemorrhages or lipid exudates may be developing in the retina.",
            Arrays.asList(
                    "Consult an ophthalmologist for a comprehensive dilated examination and potential Optical Coherence Tomography (OCT) to assess macular health.",
                    "Work with your clinical team to optimize blood sugar, HbA1c, and cardiovascular risk factors.",
                    "Monitor for any subtle visual symptoms such as blurred vision, floaters, or dark spots, and seek prompt care if changes occur."
            ),
            R.color.netra_warning,
            R.color.netra_amber_tint,
            R.drawable.bg_pill_status_acceptable
    ),

    GRADE_3(
            3,
            "Grade 3",
            "Severe NPDR",
            "Severe Non-Proliferative Diabetic Retinopathy",
            "Advanced non-proliferative vascular disruption (4-2-1 rule criteria) indicating high progression risk.",
            "This category represents advanced non-proliferative retinal changes, frequently characterized by extensive intraretinal hemorrhages across all four quadrants, definite venous beading, or prominent intraretinal microvascular abnormalities (IRMA). This stage carries a significant risk of progression to proliferative disease.",
            Arrays.asList(
                    "Promptly consult a retina specialist or ophthalmologist for formal clinical staging within 2 to 4 weeks.",
                    "Discuss specialized monitoring and preventative treatment protocols with your eye care team.",
                    "Avoid unmonitored strenuous physical exertion or Valsalva straining until clinical clearance is obtained."
            ),
            R.color.netra_orange,
            R.color.netra_orange_tint,
            R.drawable.bg_pill_status_acceptable
    ),

    GRADE_4(
            4,
            "Grade 4",
            "Proliferative DR",
            "Proliferative Diabetic Retinopathy",
            "High-severity retinal findings with abnormal neovascularization or pre-retinal involvement.",
            "This category represents the highest severity tier in the five-grade classification system. It is characterized by pathological new blood vessel growth (neovascularization) along the retina or optic disc, or pre-retinal/vitreous hemorrhage. Prompt medical evaluation is critical to safeguard visual function.",
            Arrays.asList(
                    "Seek urgent evaluation by an ophthalmologist or vitreoretinal specialist without delay.",
                    "Timely clinical interventions (such as targeted laser photocoagulation or anti-VEGF therapy) can preserve vision.",
                    "Immediately contact an emergency ophthalmic service if you experience sudden vision loss, a dark curtain over your visual field, or flashes of light."
            ),
            R.color.netra_critical,
            R.color.netra_red_tint,
            R.drawable.bg_pill_status_poor
    );

    private final int gradeNumber;
    private final String gradeBadgeText;
    private final String shortCategoryName;
    private final String fullCategoryName;
    private final String technicalSummary;
    private final String clinicalInterpretation;
    private final List<String> recommendedNextSteps;
    private final int colorResId;
    private final int tintColorResId;
    private final int badgeBgResId;

    DrSeverityGrade(
            int gradeNumber,
            String gradeBadgeText,
            String shortCategoryName,
            String fullCategoryName,
            String technicalSummary,
            String clinicalInterpretation,
            List<String> recommendedNextSteps,
            int colorResId,
            int tintColorResId,
            int badgeBgResId
    ) {
        this.gradeNumber = gradeNumber;
        this.gradeBadgeText = gradeBadgeText;
        this.shortCategoryName = shortCategoryName;
        this.fullCategoryName = fullCategoryName;
        this.technicalSummary = technicalSummary;
        this.clinicalInterpretation = clinicalInterpretation;
        this.recommendedNextSteps = Collections.unmodifiableList(recommendedNextSteps);
        this.colorResId = colorResId;
        this.tintColorResId = tintColorResId;
        this.badgeBgResId = badgeBgResId;
    }

    public int getGradeNumber() {
        return gradeNumber;
    }

    public String getGradeBadgeText() {
        return gradeBadgeText;
    }

    public String getShortCategoryName() {
        return shortCategoryName;
    }

    public String getFullCategoryName() {
        return fullCategoryName;
    }

    public String getTechnicalSummary() {
        return technicalSummary;
    }

    public String getClinicalInterpretation() {
        return clinicalInterpretation;
    }

    public List<String> getRecommendedNextSteps() {
        return recommendedNextSteps;
    }

    public int getColorResId() {
        return colorResId;
    }

    public int getTintColorResId() {
        return tintColorResId;
    }

    public int getBadgeBgResId() {
        return badgeBgResId;
    }

    public static DrSeverityGrade fromInt(int grade) {
        for (DrSeverityGrade g : values()) {
            if (g.gradeNumber == grade) {
                return g;
            }
        }
        return null;
    }

    public static DrSeverityGrade fromGradeNumber(int grade) {
        return fromInt(grade);
    }

    public static DrSeverityGrade fromLabel(String label) {
        if (label == null) return null;
        String clean = label.trim().toLowerCase();
        for (DrSeverityGrade g : values()) {
            if (g.name().equalsIgnoreCase(label)
                    || g.gradeBadgeText.equalsIgnoreCase(label)
                    || g.shortCategoryName.equalsIgnoreCase(label)
                    || clean.contains("grade " + g.gradeNumber)) {
                return g;
            }
        }
        return null;
    }
}

