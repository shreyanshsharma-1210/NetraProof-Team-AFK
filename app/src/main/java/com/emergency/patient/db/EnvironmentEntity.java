package com.emergency.patient.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "environment_records")
public class EnvironmentEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String location;          // e.g. "Mhow, Indore, MP"
    public String temperature;       // e.g. "24°C"
    public String humidity;          // e.g. "88%"
    public String aqi;               // e.g. "42 AQI (Fresh Rain)"
    public String uvIndex;           // e.g. "2 (Low)"
    public String weatherCondition;  // e.g. "Heavy Monsoon Rain"
    public String vectorRisk;        // e.g. "Elevated Dengue & Malaria Risk"
    public long timestampMillis;

    public EnvironmentEntity() {}

    @androidx.room.Ignore
    public EnvironmentEntity(String location, String temperature, String humidity,
                             String aqi, String uvIndex, String weatherCondition,
                             String vectorRisk, long timestampMillis) {
        this.location = location;
        this.temperature = temperature;
        this.humidity = humidity;
        this.aqi = aqi;
        this.uvIndex = uvIndex;
        this.weatherCondition = weatherCondition;
        this.vectorRisk = vectorRisk;
        this.timestampMillis = timestampMillis;
    }
}
