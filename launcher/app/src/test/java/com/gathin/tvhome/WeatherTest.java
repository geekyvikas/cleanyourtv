package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherTest {

    private static final String ANSWER = "{\"latitude\":54.69,\"longitude\":25.28,\"current_units\":"
            + "{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
            + "\"current\":{\"time\":\"2026-10-21T18:00\",\"interval\":900,\"temperature_2m\":24.6,\"weather_code\":45}}";

    @Test
    public void readsTheCurrentBlockNotTheUnits() {
        Weather.Now now = Weather.parse(ANSWER);
        assertNotNull(now);
        assertEquals(25, now.tempC);
        assertEquals(Weather.FOG, now.kind);
    }

    @Test
    public void negativeTemperatures() {
        Weather.Now now = Weather.parse("{\"current\":{\"temperature_2m\":-3.4,\"weather_code\":73}}");
        assertEquals(-3, now.tempC);
        assertEquals(Weather.SNOW, now.kind);
    }

    @Test
    public void incompleteOrUnknownAnswers() {
        assertNull(Weather.parse(null));
        assertNull(Weather.parse("{\"error\":true,\"reason\":\"bad\"}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":20}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":20,\"weather_code\":42}}"));
    }

    @Test
    public void wmoCodes() {
        assertEquals(Weather.CLEAR, Weather.kind(0));
        assertEquals(Weather.CLOUDY, Weather.kind(3));
        assertEquals(Weather.DRIZZLE, Weather.kind(53));
        assertEquals(Weather.RAIN, Weather.kind(63));
        assertEquals(Weather.SHOWERS, Weather.kind(81));
        assertEquals(Weather.SNOW, Weather.kind(86));
        assertEquals(Weather.THUNDERSTORM, Weather.kind(95));
        assertEquals(-1, Weather.kind(10));
    }

    @Test
    public void urlNeedsBothCoordinates() {
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=54.69&longitude=25.28"
                + "&current=temperature_2m,weather_code", Weather.url("54.69", " 25.28 "));
        assertNull(Weather.url("", ""));
        assertNull(Weather.url("54.69", "east"));
        assertNull(Weather.url("128", "77"));
    }
}
