package com.emergency.patient.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DiabeticDoctor — Model representing a verified diabetic specialist doctor.
 * Specifically covers:
 *  - Vitreo-Retinal Ophthalmologists (Diabetic Retinopathy, DME, Anti-VEGF, Laser)
 *  - Diabetologists & Endocrinologists (HbA1c, Insulin Optimization, Glycemic Management)
 *  - Diabetic Nephrologists (Diabetic Kidney Disease, Microalbuminuria, Renal Protection)
 *  - Podiatrists & Diabetic Foot Surgeons (Neuropathy, Foot Ulcers, Wound Care)
 *  - Diabetic Dietitians & Educators (Medical Nutrition Therapy, Carb Counting)
 */
public class DiabeticDoctor implements Serializable {

    public static final String CAT_ALL = "ALL";
    public static final String CAT_RETINA = "RETINA";
    public static final String CAT_DIABETOLOGY = "DIABETOLOGY";
    public static final String CAT_NEPHROLOGY = "NEPHROLOGY";
    public static final String CAT_PODIATRY = "PODIATRY";
    public static final String CAT_NUTRITION = "NUTRITION";

    private String id;
    private String name;
    private String initials;
    private String qualification;
    private String specialtyCategory;
    private String specialtyTitle;
    private String hospitalOrClinic;
    private String city;
    private String locality;
    private double latitude;
    private double longitude;
    private double distanceKm;
    private int experienceYears;
    private double rating;
    private int reviewCount;
    private String consultationFee;
    private String nextSlot;
    private String phone;
    private List<String> conditionsTreated;
    private boolean onlineForTeleconsult;
    private String badgeText;

    public DiabeticDoctor(String id, String name, String initials, String qualification,
                          String specialtyCategory, String specialtyTitle, String hospitalOrClinic,
                          String city, String locality, double latitude, double longitude,
                          int experienceYears, double rating, int reviewCount,
                          String consultationFee, String nextSlot, String phone,
                          List<String> conditionsTreated, boolean onlineForTeleconsult,
                          String badgeText) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.qualification = qualification;
        this.specialtyCategory = specialtyCategory;
        this.specialtyTitle = specialtyTitle;
        this.hospitalOrClinic = hospitalOrClinic;
        this.city = city;
        this.locality = locality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.experienceYears = experienceYears;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.consultationFee = consultationFee;
        this.nextSlot = nextSlot;
        this.phone = phone;
        this.conditionsTreated = conditionsTreated != null ? conditionsTreated : new ArrayList<>();
        this.onlineForTeleconsult = onlineForTeleconsult;
        this.badgeText = badgeText;
        this.distanceKm = 0.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getInitials() { return initials; }
    public String getQualification() { return qualification; }
    public String getSpecialtyCategory() { return specialtyCategory; }
    public String getSpecialtyTitle() { return specialtyTitle; }
    public String getHospitalOrClinic() { return hospitalOrClinic; }
    public String getCity() { return city; }
    public String getLocality() { return locality; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public int getExperienceYears() { return experienceYears; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public String getConsultationFee() { return consultationFee; }
    public String getNextSlot() { return nextSlot; }
    public String getPhone() { return phone; }
    public List<String> getConditionsTreated() { return conditionsTreated; }
    public boolean isOnlineForTeleconsult() { return onlineForTeleconsult; }
    public String getBadgeText() { return badgeText; }

    public String getFormattedDistance() {
        if (distanceKm <= 0.0) return "Nearby";
        if (distanceKm < 1.0) {
            return String.format(java.util.Locale.US, "%.0f m away", distanceKm * 1000);
        }
        return String.format(java.util.Locale.US, "%.1f km away", distanceKm);
    }
}

