package com.emergency.patient.scheduling;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class DosageParserTest {

    @Test
    public void testOnceDaily() {
        ScheduleData data = DosageParser.parse("1 tablet daily");
        assertEquals("DAILY", data.frequency);
        assertEquals(1, data.times.size());
        assertEquals("09:00", data.times.get(0));
    }

    @Test
    public void testTwiceDaily() {
        ScheduleData data = DosageParser.parse("Take 1 pill twice a day");
        assertEquals("TWICE_DAILY", data.frequency);
        assertEquals(2, data.times.size());
        assertEquals("08:00", data.times.get(0));
        assertEquals("20:00", data.times.get(1));
    }

    @Test
    public void testThriceDaily() {
        ScheduleData data = DosageParser.parse("Take 1 tablet 3 times a day");
        assertEquals("THRICE_DAILY", data.frequency);
        assertEquals(3, data.times.size());
        assertEquals("08:00", data.times.get(0));
        assertEquals("14:00", data.times.get(1));
        assertEquals("20:00", data.times.get(2));
    }

    @Test
    public void testFourTimesDaily() {
        ScheduleData data = DosageParser.parse("Take 1 tablet 4 times a day");
        assertEquals("FOUR_TIMES_DAILY", data.frequency);
        assertEquals(4, data.times.size());
        assertEquals("08:00", data.times.get(0));
        assertEquals("12:00", data.times.get(1));
        assertEquals("16:00", data.times.get(2));
        assertEquals("20:00", data.times.get(3));
    }

    @Test
    public void testTimeFormatting() {
        assertEquals("08:00 AM", DosageParser.formatTime12H("08:00"));
        assertEquals("02:30 PM", DosageParser.formatTime12H("14:30"));
        assertEquals("12:00 PM", DosageParser.formatTime12H("12:00"));
        assertEquals("08:00 PM", DosageParser.formatTime12H("20:00"));
    }
}
