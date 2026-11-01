package com.emergency.patient.luna.domain;

import com.emergency.patient.luna.model.Contradiction;
import com.emergency.patient.luna.model.ContraSeverity;
import com.emergency.patient.luna.model.ContraType;
import com.emergency.patient.luna.model.CyclePhase;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Static knowledge base for drug–drug, drug–phase, and drug–nutrient contradictions.
 * All data is hardcoded — no network dependency — providing instant offline results.
 */
public class ContraDatabase {

    private static final List<Contradiction> ALL = Arrays.asList(

        // ═══ DRUG–DRUG ═══════════════════════════════════════════════════════

        new Contradiction(
            "tranexamic_ocp",
            ContraType.DRUG_DRUG, ContraSeverity.CRITICAL,
            "tranexamic_acid", "hormonal_contraceptive",
            null, null,
            "Concurrent Tranexamic Acid and Estrogen-containing OCPs increases thrombosis risk.",
            "Tranexamic acid inhibits fibrinolysis while combined oral contraceptives increase hepatic clotting factors. Co-administration significantly elevates venous thromboembolism (VTE) risk.",
            Arrays.asList(
                "Elevated deep vein thrombosis (DVT) risk",
                "Severe pelvic cramping warning",
                "Requires physician co-prescription approval"
            ),
            "Do not take together without explicit specialist haematology guidance. Consider non-hormonal flow management.",
            false
        ),

        new Contradiction(
            "ssri_hormonal",
            ContraType.DRUG_DRUG, ContraSeverity.CRITICAL,
            "sertraline", "hormonal_contraceptive",
            null, null,
            "SSRIs can reduce hormonal contraceptive efficacy and amplify cycle disruption.",
            "Sertraline inhibits CYP3A4, the enzyme responsible for metabolising synthetic estrogens and progestins. Plasma hormone levels may fall 30–40%, reducing contraceptive reliability. Combined serotonin + hormone fluctuations worsen PMDD.",
            Arrays.asList(
                "Reduced contraceptive reliability",
                "Cycle length shift of 3–7 days",
                "Worsens luteal-phase mood symptoms",
                "May elevate prolactin indirectly"
            ),
            "Discuss with prescriber. Consider non-hormonal contraception or confirm overlap timing. Do not stop either medication without medical advice.",
            false
        ),

        new Contradiction(
            "ssri_thyroid",
            ContraType.DRUG_DRUG, ContraSeverity.MODERATE,
            "sertraline", "levothyroxine",
            null, null,
            "SSRIs can interfere with thyroid hormone absorption when taken close together.",
            "Sertraline may reduce absorption of levothyroxine by up to 25% if taken within 2 hours. Thyroid hormone directly regulates cycle regularity — even mild hypothyroidism from reduced absorption can delay ovulation.",
            Arrays.asList(
                "Delayed ovulation",
                "Irregular cycles if thyroid levels affected",
                "Fatigue amplified"
            ),
            "Take levothyroxine at least 4 hours before or after sertraline. Monitor TSH levels regularly.",
            false
        ),

        // ═══ DRUG–PHASE ══════════════════════════════════════════════════════

        new Contradiction(
            "mefenamic_menstrual",
            ContraType.DRUG_PHASE, ContraSeverity.INFORMATIONAL,
            "mefenamic_acid", null,
            CyclePhase.MENSTRUAL, null,
            "Mefenamic acid effectively reduces menstrual flow volume and uterine cramping.",
            "Mefenamic acid is an NSAID with dual action: it inhibits COX-1/COX-2 and directly blocks prostaglandin receptors in the myometrium, reducing painful menstrual cramps and blood loss by 20–35%.",
            Arrays.asList(
                "Significantly alleviates primary dysmenorrhea",
                "Reduces menorrhagia / heavy blood flow",
                "Best taken with meals to prevent gastric irritation"
            ),
            "Take with food at the earliest onset of cramps. Limit continuous use to 3-5 days during active menstruation.",
            true
        ),

        new Contradiction(
            "norethisterone_delay",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "norethisterone", null,
            CyclePhase.LUTEAL, null,
            "Progestin prevents endometrial shedding, postponing menstrual bleeding onset.",
            "Synthetic progestins maintain high luteal-phase endometrium support, delaying menstruation for the duration of intake. Upon cessation, withdrawal bleeding typically occurs in 2-4 days accompanied by temporary cramps.",
            Arrays.asList(
                "Postpones menstrual bleeding",
                "May cause mild transient breast tenderness & bloating",
                "Alters basal body temperature readings"
            ),
            "Take strictly as prescribed. Track cycle day carefully for prediction recalibration.",
            false
        ),

        new Contradiction(
            "nsaid_luteal",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "ibuprofen", null,
            CyclePhase.LUTEAL, null,
            "NSAIDs during the late luteal phase may inhibit prostaglandins needed for menstruation onset.",
            "Ibuprofen suppresses COX-1/COX-2 enzymes, reducing prostaglandin synthesis. In the late luteal phase, prostaglandins trigger uterine contractions that initiate menstruation. Heavy use can delay or lighten periods.",
            Arrays.asList(
                "May delay period onset by 1–3 days",
                "Can mask early cramping patterns",
                "Alters natural uterine shedding"
            ),
            "Use sparingly (1–2 days max). Paracetamol is a safer baseline for general headaches.",
            true
        ),

        new Contradiction(
            "nsaid_ovulation",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "ibuprofen", null,
            CyclePhase.OVULATION, null,
            "NSAID use during ovulation may delay or block egg release (LUF syndrome).",
            "High-dose NSAIDs around ovulation prevent the LH-triggered follicle rupture, causing Luteinised Unruptured Follicle (LUF) syndrome — delaying cycle progression.",
            Arrays.asList(
                "Ovulation may be postponed",
                "Extends follicular phase length",
                "Can cause a missed or delayed period"
            ),
            "Avoid high-dose NSAIDs during the 3-day fertile window.",
            true
        ),

        // ═══ DRUG–NUTRIENT ════════════════════════════════════════════════════

        new Contradiction(
            "iron_post_menstrual",
            ContraType.DRUG_NUTRIENT, ContraSeverity.INFORMATIONAL,
            "iron", null,
            CyclePhase.FOLLICULAR, "Vitamin C",
            "Post-menstrual iron supplementation restores ferritin stores lost during menstruation.",
            "Menstrual blood loss is the leading cause of microcytic anaemia in reproductive-age women. Pairing oral iron with Vitamin C enhances bioavailability by up to 67%.",
            Arrays.asList(
                "Replenishes hemoglobin & ferritin stores",
                "Combats post-period fatigue and brain fog",
                "Avoid taking iron alongside dairy/calcium (inhibits absorption)"
            ),
            "Take iron with orange juice or Vitamin C. Separate from tea/coffee by at least 2 hours.",
            true
        ),
        new Contradiction(
            "metformin_b12",
            ContraType.DRUG_NUTRIENT, ContraSeverity.INFORMATIONAL,
            "metformin", null,
            null, "Vitamin B12",
            "Long-term Metformin may reduce B12 absorption, compounding post-menstrual fatigue.",
            "Metformin reduces absorption of Vitamin B12 (up to 30%) by competing for " +
            "intrinsic-factor-mediated uptake in the ileum. Iron losses during menstruation " +
            "compound this risk, increasing mild anaemia likelihood.",
            Arrays.asList(
                "Increased anaemia risk post-period",
                "Fatigue and brain fog",
                "May worsen menstrual-phase energy dip"
            ),
            "Monitor B12 and iron levels annually. Increase iron-rich foods post-period. " +
            "Consider B12 supplementation.",
            true
        ),

        new Contradiction(
            "ssri_magnesium",
            ContraType.DRUG_NUTRIENT, ContraSeverity.INFORMATIONAL,
            "sertraline", null,
            null, "Magnesium",
            "SSRIs may deplete magnesium over time, which is critical for reducing PMS symptoms.",
            "Chronic SSRI use is associated with reduced intracellular magnesium levels. " +
            "Magnesium deficiency amplifies luteal-phase anxiety, cramps, and sleep disruption.",
            Arrays.asList(
                "Worsened PMS symptoms",
                "Increased cramping",
                "Sleep quality reduction in luteal phase"
            ),
            "Supplement with magnesium glycinate 200–400mg in the luteal phase. " +
            "Increase magnesium-rich foods: dark chocolate, spinach, almonds.",
            true
        ),
        // ═══ ANTIBIOTIC & REPRODUCTIVE CYCLE INTERACTIONS ═══════════════════════

        new Contradiction(
            "antibiotic_ocp_breakthrough",
            ContraType.DRUG_DRUG, ContraSeverity.CRITICAL,
            "amoxicillin", "hormonal_contraceptive",
            null, null,
            "Broad-spectrum Antibiotics disrupt contraceptive gut recirculation, causing spotting & ovulation risk.",
            "Antibiotics decimate intestinal flora producing beta-glucuronidase, suppressing the enterohepatic recirculation of ethinylestradiol. This causes a sudden drop in plasma hormone levels, leading to breakthrough bleeding, irregular spotting, and possible escape ovulation.",
            Arrays.asList(
                "Breakthrough mid-cycle spotting & bleeding",
                "Substantially compromised oral contraceptive reliability",
                "Unpredictable shift in withdrawal bleed timing"
            ),
            "Use secondary barrier contraception (condoms) during the entire antibiotic regimen + 7 days post-treatment.",
            false
        ),

        new Contradiction(
            "antibiotic_luteal_dysbiosis",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "amoxicillin", null,
            CyclePhase.LUTEAL, null,
            "Antibiotics during progesterone peak amplify vaginal microbiome disruption and yeast overgrowth.",
            "High luteal progesterone enriches vaginal epithelial glycogen. Broad-spectrum antibiotics eradicate protective Lactobacillus crispatus, rapidly triggering Candida albicans overgrowth and intense pelvic/vulvar burning.",
            Arrays.asList(
                "Acute vulvovaginal candidiasis vulnerability",
                "Pelvic discomfort that mimics premenstrual cramps",
                "Disrupted vaginal pH balance"
            ),
            "Co-prescribe targeted oral probiotics (L. rhamnosus & L. reuteri) taken 3 hours apart from antibiotic doses.",
            true
        ),

        new Contradiction(
            "ssri_prolactin_delay",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "sertraline", null,
            CyclePhase.LUTEAL, null,
            "SSRIs raise serum prolactin, causing luteal phase prolongation, breast tenderness, and period delays.",
            "Serotonin reuptake inhibition reduces tuberoinfundibular dopamine transmission, causing hyperprolactinemia. Elevated prolactin impairs pulsatile GnRH secretion, delaying the onset of menstruation by 3 to 7 days and exacerbating fibrocystic breast pain.",
            Arrays.asList(
                "Delayed period start by 3–7 days (oligomenorrhea)",
                "Amplified breast swelling and tenderness",
                "Luteal phase defect / mood volatility"
            ),
            "If menstruation is delayed >7 days with negative hCG test, request a serum prolactin check from your gynecologist.",
            true
        ),

        new Contradiction(
            "nsaid_rebound_cramps",
            ContraType.DRUG_PHASE, ContraSeverity.MODERATE,
            "ibuprofen", null,
            CyclePhase.LUTEAL, null,
            "Continuous late-luteal NSAID use suppresses normal shedding and triggers severe rebound cramps.",
            "Excessive COX-2 inhibition in the premenstrual window prevents the synchronized endometrial prostaglandin cascade needed for smooth shedding. Discontinuing the NSAID triggers a rebound prostaglandin spike, resulting in painful, uncoordinated uterine spasms.",
            Arrays.asList(
                "Postpones initial period bleeding",
                "Severe rebound uterine cramping once medication clears",
                "Dark, clotted initial menstrual flow"
            ),
            "Limit high-dose NSAIDs to active bleeding days only; switch to magnesium glycinate and heat therapy pre-period.",
            true
        ),

        new Contradiction(
            "doxycycline_contraceptive",
            ContraType.DRUG_DRUG, ContraSeverity.CRITICAL,
            "doxycycline", "hormonal_contraceptive",
            null, null,
            "Tetracycline antibiotics reduce estrogen absorption and increase irregular spotting.",
            "Doxycycline alters gastrointestinal motility and reduces steroid conjugate hydrolysis in the distal ileum, lowering systemic estrogen levels and inducing erratic uterine spotting.",
            Arrays.asList(
                "Breakthrough spotting during active pill pack",
                "Potential reduction in contraceptive efficacy",
                "Increased gastrointestinal distress"
            ),
            "Maintain back-up barrier contraception for the full antibiotic course plus 7 consecutive days.",
            false
        )
    );

    // ─── Public query methods ─────────────────────────────────────────────────

    /** Returns all contradictions that involve any of the given drug names. */
    public static List<Contradiction> findForDrugs(List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (matchesDrug(c, lower)) result.add(c);
        }
        return result;
    }

    /** Returns DRUG_PHASE contradictions active in the given phase that involve user's drugs. */
    public static List<Contradiction> findForPhase(CyclePhase phase, List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (c.type == ContraType.DRUG_PHASE
                    && (c.phaseTarget == null || c.phaseTarget == phase)
                    && containsDrug(lower, c.drugA)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Returns DRUG_NUTRIENT contradictions for the given drug list. */
    public static List<Contradiction> findNutrientConflicts(List<String> drugNames) {
        List<String> lower = toLower(drugNames);
        List<Contradiction> result = new ArrayList<>();
        for (Contradiction c : ALL) {
            if (c.type == ContraType.DRUG_NUTRIENT && containsDrug(lower, c.drugA)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Full analysis — deduplicates and sorts CRITICAL → MODERATE → INFORMATIONAL. */
    public static List<Contradiction> analyzeAll(List<String> drugNames, CyclePhase phase) {
        List<Contradiction> combined = new ArrayList<>();
        combined.addAll(findForDrugs(drugNames));
        combined.addAll(findForPhase(phase, drugNames));
        combined.addAll(findNutrientConflicts(drugNames));

        // Deduplicate by id
        List<Contradiction> deduped = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Contradiction c : combined) {
            if (!seen.contains(c.id)) { seen.add(c.id); deduped.add(c); }
        }

        // Sort: CRITICAL first
        deduped.sort((a, b) -> a.severity.ordinal() - b.severity.ordinal());
        return deduped;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static List<String> toLower(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) if (s != null) result.add(s.toLowerCase());
        return result;
    }

    private static boolean matchesDrug(Contradiction c, List<String> lower) {
        if (c.drugB != null) {
            // Must have both drugA and drugB
            return containsDrug(lower, c.drugA) && containsDrug(lower, c.drugB);
        }
        return containsDrug(lower, c.drugA);
    }

    private static boolean containsDrug(List<String> userDrugs, String targetDrugKey) {
        if (targetDrugKey == null) return false;
        String target = targetDrugKey.toLowerCase();

        for (String raw : userDrugs) {
            if (raw == null) continue;
            String d = raw.toLowerCase();

            // Direct substring
            if (d.contains(target) || target.contains(d)) return true;

            // Hormonal contraceptive aliases
            if (target.equals("hormonal_contraceptive")) {
                if (d.contains("yaz") || d.contains("ethinylestradiol") || d.contains("drospirenone")
                        || d.contains("contracept") || d.contains("birth control") || d.contains("desogestrel")
                        || d.contains("levonorgestrel") || d.contains("progest") || d.contains("estrogen")
                        || d.contains("ocp") || d.contains("pill")) {
                    return true;
                }
            }

            // Antibiotic aliases (Amoxicillin, Augmentin, Doxycycline, Ciprofloxacin)
            if (target.equals("amoxicillin")) {
                if (d.contains("amox") || d.contains("augmentin") || d.contains("clavulan") || d.contains("antibiotic") || d.contains("mox")) {
                    return true;
                }
            }

            if (target.equals("doxycycline")) {
                if (d.contains("doxy") || d.contains("vibra") || d.contains("tetracyclin")) {
                    return true;
                }
            }

            // SSRI aliases
            if (target.equals("sertraline")) {
                if (d.contains("zoloft") || d.contains("ssri") || d.contains("fluoxetine") || d.contains("prozac") || d.contains("escitalopram")) {
                    return true;
                }
            }

            // NSAID aliases
            if (target.equals("ibuprofen")) {
                if (d.contains("advil") || d.contains("motrin") || d.contains("brufen") || d.contains("nsaid") || d.contains("naproxen")) {
                    return true;
                }
            }

            // Mefenamic acid aliases
            if (target.equals("mefenamic_acid") || target.equals("mefenamic")) {
                if (d.contains("mefenamic") || d.contains("meftal") || d.contains("ponstan")) {
                    return true;
                }
            }

            // Iron aliases
            if (target.equals("iron")) {
                if (d.contains("ferrous") || d.contains("autrin") || d.contains("folic") || d.contains("hemoglobin")) {
                    return true;
                }
            }

            // Tranexamic acid aliases
            if (target.equals("tranexamic_acid") || target.equals("tranexamic")) {
                if (d.contains("tranexamic") || d.contains("pause") || d.contains("cyklokapron") || d.contains("txa")) {
                    return true;
                }
            }
        }
        return false;
    }
}
