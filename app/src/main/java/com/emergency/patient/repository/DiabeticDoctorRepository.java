package com.emergency.patient.repository;

import com.emergency.patient.models.DiabeticDoctor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * DiabeticDoctorRepository — Data repository for verified diabetic specialist doctors.
 * 
 * Specifically filters and delivers specialists who diagnose and treat diabetic
 * complications (Diabetic Retinopathy, Macular Edema, Diabetic Nephropathy,
 * Neuropathy, Diabetic Foot Ulcers, and Glycemic Management).
 */
public class DiabeticDoctorRepository {

    private static DiabeticDoctorRepository instance;
    private final List<DiabeticDoctor> masterDoctorList = new ArrayList<>();

    public static synchronized DiabeticDoctorRepository getInstance() {
        if (instance == null) {
            instance = new DiabeticDoctorRepository();
        }
        return instance;
    }

    private DiabeticDoctorRepository() {
        seedDoctors();
    }

    private void seedDoctors() {
        // ══════════════════════════════════════════════════════════════════
        // INDORE (Primary Local Hub)
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-01",
                "Dr. Arvind Shrivastava",
                "AS",
                "MBBS, MS (Ophth), FVR (Sankara Nethralaya)",
                DiabeticDoctor.CAT_RETINA,
                "Senior Vitreo-Retinal Surgeon",
                "Netra Diabetic Eye & Laser Centre",
                "Indore",
                "Navlakha",
                22.7050, 75.8650,
                16, 4.9, 142,
                "₹700",
                "Today at 03:30 PM",
                "+91 731 4056789",
                Arrays.asList("Diabetic Retinopathy", "Macular Edema (DME)", "Anti-VEGF Injections", "Retinal Laser"),
                true,
                "⭐ Top Retinal Specialist"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-02",
                "Dr. Shalini Mehta",
                "SM",
                "MD, DM (Endocrinology, AIIMS Delhi)",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Chief Diabetologist & Endocrinologist",
                "Apollo Sugar Clinics",
                "Indore",
                "Vijay Nagar",
                22.7533, 75.8937,
                14, 4.8, 128,
                "₹800",
                "Today at 04:15 PM",
                "+91 731 4239800",
                Arrays.asList("Type 1 & 2 Diabetes", "HbA1c > 9.0 Management", "Insulin Pump Therapy", "CGM Monitoring"),
                true,
                "⚡ Available in 15 mins"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-03",
                "Dr. Rajesh K. Bansal",
                "RB",
                "MD (Med), DNB (Nephrology)",
                DiabeticDoctor.CAT_NEPHROLOGY,
                "Diabetic Renal Specialist & Nephrologist",
                "Indore Renal & Diabetic Institute",
                "Indore",
                "South Tukoganj",
                22.7220, 75.8780,
                18, 4.9, 96,
                "₹900",
                "Tomorrow at 10:30 AM",
                "+91 731 2511422",
                Arrays.asList("Diabetic Kidney Disease", "Microalbuminuria", "eGFR Decline Management", "Renal Protection"),
                true,
                "🏥 Super-specialist"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-04",
                "Dr. Priya Nair",
                "PN",
                "MS (Gen Surg), Fellowship in Diabetic Foot (UK)",
                DiabeticDoctor.CAT_PODIATRY,
                "Podiatric Surgeon & Diabetic Foot Specialist",
                "Care Diabetic Foot & Wound Clinic",
                "Indore",
                "New Palasia",
                22.7275, 75.8820,
                12, 4.7, 85,
                "₹650",
                "Today at 05:00 PM",
                "+91 731 4088910",
                Arrays.asList("Diabetic Foot Ulcers", "Peripheral Neuropathy", "Charcot Foot", "Limb Salvage & Debridement"),
                true,
                "🦶 Foot Care Expert"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-05",
                "Dr. Neha Deshmukh",
                "ND",
                "M.Sc (Clinical Nutrition), CDE (Diabetic Educator)",
                DiabeticDoctor.CAT_NUTRITION,
                "Certified Diabetes Educator & Clinical Dietitian",
                "Metabolic Health & Diabetes Education Hub",
                "Indore",
                "Old Palasia",
                22.7290, 75.8870,
                9, 4.8, 114,
                "₹500",
                "Today at 02:45 PM",
                "+91 731 2548900",
                Arrays.asList("Carbohydrate Counting", "Glycemic Index Diet", "Hypoglycemia Prevention", "Insulin-Carb Ratios"),
                true,
                "🥗 Diet & Lifestyle"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-IND-06",
                "Dr. Alok Verma",
                "AV",
                "MBBS, DNB (Ophthalmology), Vitreo-Retina Fellowship",
                DiabeticDoctor.CAT_RETINA,
                "Vitreo-Retina & Cataract Specialist",
                "Drishti Eye & Retina Centre",
                "Indore",
                "Geeta Bhawan",
                22.7160, 75.8750,
                11, 4.6, 73,
                "₹600",
                "Tomorrow at 11:00 AM",
                "+91 731 2490123",
                Arrays.asList("NPDR & PDR Screening", "Vitreous Hemorrhage", "Fundus Angiography", "Intravitreal Injections"),
                true,
                "👁️ Retinal Laser"
        ));

        // ══════════════════════════════════════════════════════════════════
        // NEW DELHI / NCR
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-DEL-01",
                "Dr. Manish Tandon",
                "MT",
                "MS (Ophth), Fellowship Vitreo-Retinal Surgery",
                DiabeticDoctor.CAT_RETINA,
                "Director of Vitreo-Retinal Services",
                "Dr. Shroff's Charity Eye Hospital",
                "New Delhi",
                "Daryaganj",
                28.6448, 77.2405,
                20, 4.9, 310,
                "₹1000",
                "Today at 03:00 PM",
                "+91 11 43524444",
                Arrays.asList("Severe NPDR / PDR", "Tractional Retinal Detachment", "Macular Edema Laser", "Anti-VEGF"),
                true,
                "⭐ Renowned Retina Surgeon"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-DEL-02",
                "Dr. Ashok Jhingan",
                "AJ",
                "MD (Medicine), FICA (USA), Diabetology Pioneer",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Senior Consultant Diabetologist",
                "Delhi Diabetes Research Centre",
                "New Delhi",
                "Rajouri Garden",
                28.6500, 77.1200,
                26, 4.9, 450,
                "₹1200",
                "Today at 04:30 PM",
                "+91 11 25102000",
                Arrays.asList("Intensive Glycemic Control", "Brittle Diabetes", "Diabetic Neuropathy", "Insulin Resistance"),
                true,
                "🏆 25+ Yrs Diabetic Research"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-DEL-03",
                "Dr. Sanjeev Saxena",
                "SS",
                "MD, DM (Nephrology, AIIMS)",
                DiabeticDoctor.CAT_NEPHROLOGY,
                "Director Diabetic Nephrology",
                "Fortis Escorts Kidney Institute",
                "New Delhi",
                "Okhla",
                28.5600, 77.2800,
                22, 4.8, 195,
                "₹1500",
                "Tomorrow at 10:00 AM",
                "+91 11 47135000",
                Arrays.asList("Diabetic Glomerulosclerosis", "Albuminuria Staging", "Hypertensive Diabetic Nephropathy"),
                true,
                "🔬 Kidney Care Lead"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-DEL-04",
                "Dr. Sanjay Sharma",
                "SS",
                "MS (Surgery), Fellow Diabetic Foot Society",
                DiabeticDoctor.CAT_PODIATRY,
                "Senior Diabetic Foot & Reconstructive Surgeon",
                "Max Super Speciality Hospital",
                "New Delhi",
                "Saket",
                28.5245, 77.2155,
                17, 4.8, 160,
                "₹1100",
                "Today at 06:00 PM",
                "+91 11 26515050",
                Arrays.asList("Complex Foot Ulcers", "Negative Pressure Wound Therapy", "Diabetic Charcot Neuroarthropathy"),
                true,
                "🦶 Limb Preservation"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-DEL-05",
                "Dr. Ritu Bhattacharya",
                "RB",
                "MD, DM (Endocrinology)",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Principal Consultant Endocrinologist",
                "Medanta - The Medicity",
                "Gurugram",
                "Sector 38",
                28.4390, 77.0420,
                15, 4.9, 210,
                "₹1300",
                "Today at 05:30 PM",
                "+91 124 4141414",
                Arrays.asList("Young Onset Diabetes", "Gestational Diabetes", "Continuous Glucose Telemetry", "Metabolic Health"),
                true,
                "⚡ Online Video Ready"
        ));

        // ══════════════════════════════════════════════════════════════════
        // MUMBAI
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-MUM-01",
                "Dr. Hemant Murthy",
                "HM",
                "MS (Ophthalmology), Vitreo-Retinal Fellowship",
                DiabeticDoctor.CAT_RETINA,
                "Senior Vitreo-Retinal Surgeon",
                "Aditya Jyot Eye Hospital",
                "Mumbai",
                "Wadala West",
                19.0180, 72.8590,
                18, 4.9, 280,
                "₹1200",
                "Today at 02:30 PM",
                "+91 22 24177600",
                Arrays.asList("Proliferative Diabetic Retinopathy", "Diabetic Macular Edema", "Retinal Angiography", "Vitrectomy"),
                true,
                "⭐ Retina Authority"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-MUM-02",
                "Dr. Shashank Joshi",
                "SJ",
                "MD, DM, FACP, Padma Shri Awardee",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Chief Endocrinologist & Diabetologist",
                "Lilavati Hospital & Research Centre",
                "Mumbai",
                "Bandra West",
                19.0510, 72.8290,
                28, 5.0, 520,
                "₹2000",
                "Tomorrow at 09:30 AM",
                "+91 22 26751000",
                Arrays.asList("Complex Diabetes Mellitus", "Cardio-Diabetic Risk Reduction", "Insulin Resistance", "CGM Protocols"),
                true,
                "🏆 National Diabetic Icon"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-MUM-03",
                "Dr. Pradeep Moonot",
                "PM",
                "MS (Ortho), Fellow Diabetic Foot (Wimbledon, UK)",
                DiabeticDoctor.CAT_PODIATRY,
                "Diabetic Foot & Ankle Specialist",
                "Breach Candy Hospital",
                "Mumbai",
                "Cumballa Hill",
                18.9710, 72.8050,
                16, 4.8, 140,
                "₹1400",
                "Today at 04:00 PM",
                "+91 22 23667788",
                Arrays.asList("Diabetic Neuropathy Screening", "Non-healing Foot Ulcers", "Preventive Diabetic Footwear"),
                true,
                "🦶 Foot & Ankle Expert"
        ));

        // ══════════════════════════════════════════════════════════════════
        // BENGALURU
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-BLR-01",
                "Dr. Mahesh Shanmugam",
                "MS",
                "DO, DNB, FRCSEd, Vitreo-Retina Fellowship",
                DiabeticDoctor.CAT_RETINA,
                "Head of Vitreo-Retinal Services",
                "Sankara Eye Hospital",
                "Bengaluru",
                "Kundalahalli",
                12.9660, 77.7180,
                22, 4.9, 340,
                "₹1000",
                "Today at 03:45 PM",
                "+91 80 28542727",
                Arrays.asList("Advanced Diabetic Eye Disease", "Micro-incision Vitrectomy", "Optical Coherence Tomography (OCT)"),
                true,
                "👁️ Sankara Retina Lead"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-BLR-02",
                "Dr. K. M. Prasanna Kumar",
                "PK",
                "MD, DM (Endocrinology, AIIMS)",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Senior Consultant Diabetologist",
                "Bangalore Diabetes Centre",
                "Bengaluru",
                "Vasanth Nagar",
                12.9900, 77.5850,
                25, 4.9, 410,
                "₹1100",
                "Today at 05:00 PM",
                "+91 80 22266000",
                Arrays.asList("Diabetic Microvascular Complications", "Insulin Titration", "Metabolic Syndrome"),
                true,
                "⭐ Top Endocrinologist"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-BLR-03",
                "Dr. Sudarshan Ballal",
                "SB",
                "MD, FRCP (London), Nephrology Specialist",
                DiabeticDoctor.CAT_NEPHROLOGY,
                "Chief Nephrologist & Chairman",
                "Manipal Hospital",
                "Bengaluru",
                "HAL Old Airport Road",
                12.9590, 77.6520,
                26, 4.9, 290,
                "₹1400",
                "Tomorrow at 11:30 AM",
                "+91 80 25024444",
                Arrays.asList("Diabetic Nephropathy Stage 1-4", "Renal Hemodynamics", "Microalbuminuria Monitoring"),
                true,
                "🔬 Nephrology Chairman"
        ));

        // ══════════════════════════════════════════════════════════════════
        // HYDERABAD
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-HYD-01",
                "Dr. Taraprasad Das",
                "TD",
                "MS, FAMS, FRCS, Vice Chair LVPEI",
                DiabeticDoctor.CAT_RETINA,
                "Distinguished Vitreo-Retinal Surgeon",
                "L V Prasad Eye Institute (LVPEI)",
                "Hyderabad",
                "Banjara Hills",
                17.4200, 78.4480,
                30, 5.0, 600,
                "₹1200",
                "Today at 04:00 PM",
                "+91 40 30612345",
                Arrays.asList("Diabetic Retinopathy Management", "Macular Edema Laser", "Complex Vitreo-Retinal Surgery"),
                true,
                "🏆 Global Retina Leader"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-HYD-02",
                "Dr. Bipin Sethi",
                "BS",
                "MD, DM (Endocrinology, PGI Chandigarh)",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Senior Consultant Endocrinologist",
                "CARE Hospitals",
                "Hyderabad",
                "Banjara Hills",
                17.4150, 78.4520,
                23, 4.9, 320,
                "₹1000",
                "Today at 02:15 PM",
                "+91 40 61656565",
                Arrays.asList("Diabetic Dyslipidemia", "Severe Glycemic Fluctuations", "Endocrine Complications"),
                true,
                "⚡ Teleconsult Ready"
        ));

        // ══════════════════════════════════════════════════════════════════
        // PUNE
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-PUN-01",
                "Dr. Aditya Kelkar",
                "AK",
                "MS, DNB, FMRF, Vitreo-Retinal Surgeon",
                DiabeticDoctor.CAT_RETINA,
                "Director Vitreo-Retina Services",
                "National Institute of Ophthalmology",
                "Pune",
                "Shivaji Nagar",
                18.5320, 73.8480,
                15, 4.8, 175,
                "₹850",
                "Today at 03:15 PM",
                "+91 20 25536333",
                Arrays.asList("Diabetic Retinopathy Screening", "Intravitreal Anti-VEGF", "Retinal Photocoagulation"),
                true,
                "👁️ Retina Specialist"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-PUN-02",
                "Dr. C. S. Yajnik",
                "CY",
                "MD, FRCP, Diabetology Research Director",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Director Diabetes Unit",
                "KEM Hospital & Research Centre",
                "Pune",
                "Rasta Peth",
                18.5200, 73.8700,
                28, 4.9, 390,
                "₹1200",
                "Tomorrow at 10:00 AM",
                "+91 20 66037300",
                Arrays.asList("Thin-Fat Indian Diabetes Phenotype", "Intensive Insulin Protocols", "Metabolic Reversal"),
                true,
                "⭐ Research Director"
        ));

        // ══════════════════════════════════════════════════════════════════
        // BHOPAL
        // ══════════════════════════════════════════════════════════════════
        masterDoctorList.add(new DiabeticDoctor(
                "DOC-BHP-01",
                "Dr. Anuj Singhal",
                "AS",
                "MS (Ophthalmology), FVR (Shroff Charity)",
                DiabeticDoctor.CAT_RETINA,
                "Chief Vitreo-Retina Consultant",
                "ASG Eye Hospital",
                "Bhopal",
                "MP Nagar Zone 2",
                23.2350, 77.4320,
                13, 4.8, 110,
                "₹700",
                "Today at 04:30 PM",
                "+91 755 4277700",
                Arrays.asList("Diabetic Retinopathy", "Anti-VEGF Therapy", "Macular Edema Laser", "Fundus Evaluation"),
                true,
                "👁️ Retina Expert"
        ));

        masterDoctorList.add(new DiabeticDoctor(
                "DOC-BHP-02",
                "Dr. K. K. Kawre",
                "KK",
                "MD (Medicine), DM (Endocrinology)",
                DiabeticDoctor.CAT_DIABETOLOGY,
                "Consultant Endocrinologist & Diabetologist",
                "AIIMS Bhopal Campus",
                "Bhopal",
                "Saket Nagar",
                23.2080, 77.4580,
                16, 4.8, 150,
                "₹600",
                "Tomorrow at 09:30 AM",
                "+91 755 2672317",
                Arrays.asList("Type 1 & 2 Glycemic Regulation", "Diabetic Neuropathy", "Gestational Diabetes"),
                true,
                "⚡ Available Today"
        ));
    }

    public List<String> getSupportedCities() {
        return Arrays.asList("Indore", "New Delhi", "Mumbai", "Bengaluru", "Hyderabad", "Pune", "Bhopal");
    }

    /**
     * Filter doctors by city, specialty category, search keyword, and calculate
     * exact distance relative to user coordinates.
     */
    public List<DiabeticDoctor> getFilteredDoctors(String selectedCity,
                                                   String selectedCategory,
                                                   String searchQuery,
                                                   double userLat,
                                                   double userLng) {
        List<DiabeticDoctor> results = new ArrayList<>();

        String queryClean = (searchQuery != null) ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";
        boolean filterCity = (selectedCity != null && !selectedCity.isEmpty() && !selectedCity.equalsIgnoreCase("All Cities") && !selectedCity.equalsIgnoreCase("GPS"));
        boolean filterCategory = (selectedCategory != null && !selectedCategory.isEmpty() && !selectedCategory.equalsIgnoreCase(DiabeticDoctor.CAT_ALL));

        for (DiabeticDoctor doc : masterDoctorList) {
            // City match
            if (filterCity) {
                if (!doc.getCity().equalsIgnoreCase(selectedCity) && !selectedCity.contains(doc.getCity())) {
                    continue;
                }
            }

            // Category match
            if (filterCategory) {
                if (!doc.getSpecialtyCategory().equalsIgnoreCase(selectedCategory)) {
                    continue;
                }
            }

            // Search query match (doctor name, hospital, locality, specialty, or condition treated)
            if (!queryClean.isEmpty()) {
                boolean matchName = doc.getName().toLowerCase(Locale.ROOT).contains(queryClean);
                boolean matchHospital = doc.getHospitalOrClinic().toLowerCase(Locale.ROOT).contains(queryClean);
                boolean matchLocality = doc.getLocality().toLowerCase(Locale.ROOT).contains(queryClean);
                boolean matchSpecialty = doc.getSpecialtyTitle().toLowerCase(Locale.ROOT).contains(queryClean);
                boolean matchCity = doc.getCity().toLowerCase(Locale.ROOT).contains(queryClean);
                boolean matchCondition = false;
                for (String condition : doc.getConditionsTreated()) {
                    if (condition.toLowerCase(Locale.ROOT).contains(queryClean)) {
                        matchCondition = true;
                        break;
                    }
                }

                if (!matchName && !matchHospital && !matchLocality && !matchSpecialty && !matchCity && !matchCondition) {
                    continue;
                }
            }

            // Calculate distance in KM using Haversine
            if (userLat != 0.0 && userLng != 0.0) {
                double dist = calculateDistanceKm(userLat, userLng, doc.getLatitude(), doc.getLongitude());
                doc.setDistanceKm(dist);
            } else {
                doc.setDistanceKm(0.0);
            }

            results.add(doc);
        }

        // Sort by distance (nearest first). If distance is 0, sort by rating descending.
        Collections.sort(results, new Comparator<DiabeticDoctor>() {
            @Override
            public int compare(DiabeticDoctor o1, DiabeticDoctor o2) {
                if (o1.getDistanceKm() > 0 && o2.getDistanceKm() > 0) {
                    return Double.compare(o1.getDistanceKm(), o2.getDistanceKm());
                }
                return Double.compare(o2.getRating(), o1.getRating());
            }
        });

        return results;
    }

    /**
     * Finds nearest supported city name based on GPS coordinates.
     */
    public String getClosestCity(double userLat, double userLng) {
        if (userLat == 0.0 && userLng == 0.0) return "Indore";

        double minDistance = Double.MAX_VALUE;
        String closest = "Indore";

        for (DiabeticDoctor doc : masterDoctorList) {
            double d = calculateDistanceKm(userLat, userLng, doc.getLatitude(), doc.getLongitude());
            if (d < minDistance) {
                minDistance = d;
                closest = doc.getCity();
            }
        }
        return closest;
    }

    /**
     * Standard Haversine formula to compute great-circle distance between two GPS coordinates in kilometers.
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in KM
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

