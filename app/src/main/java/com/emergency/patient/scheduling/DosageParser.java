package com.emergency.patient.scheduling;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DosageParser {

    /**
     * Parses natural language dosage text into structured ScheduleData using deterministic rules.
     */
    public static ScheduleData parse(String dosageText) {
        if (dosageText == null || dosageText.isEmpty()) {
            return new ScheduleData("DAILY", Arrays.asList("09:00"), "1 tablet");
        }

        String text = dosageText.toLowerCase().trim();
        ScheduleData result = new ScheduleData();
        result.times = new ArrayList<>();

        // 1. Frequency Matching (Order: 4x -> 3x -> 2x -> 1x)
        if (text.contains("four times") || text.matches(".*\\b4\\s*(times|time|pills|tablets|doses|x)\\b.*")
                || text.contains("q.i.d") || text.contains("qid") || text.contains("4 a day") || text.contains("4 times a day")) {
            result.frequency = "FOUR_TIMES_DAILY";
            result.times.addAll(getDefaultTimesForFrequency(4));
        } else if (text.contains("thrice") || text.contains("three times") || text.matches(".*\\b3\\s*(times|time|pills|tablets|doses|x)\\b.*")
                || text.contains("t.i.d") || text.contains("tid") || text.contains("3 a day") || text.contains("3 times a day")) {
            result.frequency = "THRICE_DAILY";
            result.times.addAll(getDefaultTimesForFrequency(3));
        } else if (text.contains("twice") || text.contains("two times") || text.matches(".*\\b2\\s*(times|time|pills|tablets|doses|x)\\b.*")
                || text.contains("b.i.d") || text.contains("bid") || text.contains("2 a day") || text.contains("2 times a day")
                || (text.contains("morning") && (text.contains("night") || text.contains("evening")))) {
            result.frequency = "TWICE_DAILY";
            result.times.addAll(getDefaultTimesForFrequency(2));
        } else if (text.contains("every 4 hours") || text.contains("q4h")) {
            result.frequency = "EVERY_4_HOURS";
            result.times.addAll(getDefaultTimesForFrequency(6));
        } else if (text.contains("every 6 hours") || text.contains("q6h")) {
            result.frequency = "EVERY_6_HOURS";
            result.times.addAll(getDefaultTimesForFrequency(4));
        } else if (text.contains("weekly") || text.contains("once a week")) {
            result.frequency = "WEEKLY";
            result.times.add("09:00");
        } else {
            result.frequency = "DAILY";
            result.times.add("09:00"); // Default
        }

        // 2. Timing Adjustments for single or specific dose mentions
        if (result.frequency.equals("DAILY")) {
            if (text.contains("night") || text.contains("bedtime")) {
                result.times.clear();
                result.times.add("21:00");
            } else if (text.contains("morning")) {
                result.times.clear();
                result.times.add("08:00");
            } else if (text.contains("afternoon") || text.contains("noon") || text.contains("lunch")) {
                result.times.clear();
                result.times.add("13:00");
            } else if (text.contains("evening")) {
                result.times.clear();
                result.times.add("18:00");
            }
        }

        if (text.contains("after meal") || text.contains("post meal") || text.contains("after food")) {
            result.instructions = "After meal";
        } else if (text.contains("before meal") || text.contains("empty stomach") || text.contains("before food")) {
            result.instructions = "Before meal";
        }

        // 3. Quantity Extraction
        Pattern qtyPattern = Pattern.compile("(\\d+)\\s*(tablet|tablets|pill|pills|cap|capsule|capsules|ml|mg|dose|doses)");
        Matcher matcher = qtyPattern.matcher(text);
        if (matcher.find()) {
            result.quantity = matcher.group(1) + " " + matcher.group(2);
        } else {
            result.quantity = "1 tablet";
        }

        return result;
    }

    /**
     * Returns evenly spaced default reminder times for a given daily frequency.
     */
    public static List<String> getDefaultTimesForFrequency(int timesPerDay) {
        List<String> list = new ArrayList<>();
        switch (timesPerDay) {
            case 1:
                list.add("09:00");
                break;
            case 2:
                list.addAll(Arrays.asList("08:00", "20:00"));
                break;
            case 3:
                list.addAll(Arrays.asList("08:00", "14:00", "20:00"));
                break;
            case 4:
                list.addAll(Arrays.asList("08:00", "12:00", "16:00", "20:00"));
                break;
            case 5:
                list.addAll(Arrays.asList("08:00", "11:00", "14:00", "17:00", "20:00"));
                break;
            case 6:
                list.addAll(Arrays.asList("08:00", "12:00", "16:00", "20:00", "00:00", "04:00"));
                break;
            default:
                if (timesPerDay > 0) {
                    int startHour = 8;
                    int endHour = 22;
                    int interval = Math.max(1, (endHour - startHour) / Math.max(1, timesPerDay - 1));
                    for (int i = 0; i < timesPerDay; i++) {
                        int h = Math.min(23, startHour + i * interval);
                        list.add(String.format(Locale.getDefault(), "%02d:00", h));
                    }
                } else {
                    list.add("09:00");
                }
                break;
        }
        return list;
    }

    /**
     * Formats a 24-hour time string ("HH:mm") into 12-hour format ("hh:mm a").
     */
    public static String formatTime12H(String time24H) {
        if (time24H == null || !time24H.contains(":")) return time24H != null ? time24H : "";
        try {
            String[] parts = time24H.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            String amPm = hour >= 12 ? "PM" : "AM";
            int hour12 = hour % 12;
            if (hour12 == 0) hour12 = 12;
            return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, min, amPm);
        } catch (Exception e) {
            return time24H;
        }
    }

    /**
     * Returns a human-friendly dose title (e.g. "Morning Dose", "Afternoon Dose", etc.)
     */
    public static String getSlotLabel(int slotIndex, int totalSlots) {
        if (totalSlots == 1) return "Daily Dose";
        if (totalSlots == 2) {
            return slotIndex == 0 ? "Morning Dose" : "Night Dose";
        }
        if (totalSlots == 3) {
            if (slotIndex == 0) return "Morning Dose";
            if (slotIndex == 1) return "Afternoon Dose";
            return "Night Dose";
        }
        if (totalSlots == 4) {
            if (slotIndex == 0) return "Morning Dose";
            if (slotIndex == 1) return "Noon Dose";
            if (slotIndex == 2) return "Evening Dose";
            return "Night Dose";
        }
        return "Dose " + (slotIndex + 1);
    }

    /**
     * Legacy support for frequency int extraction
     */
    public static int getFrequency(String dosageText) {
        ScheduleData data = parse(dosageText);
        return data.times != null ? data.times.size() : 1;
    }
}
