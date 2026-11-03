
package com.angamba.observability.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class WeatherService {

    private static final Logger logger =
            LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient = RestClient.create();

    private final MeterRegistry meterRegistry;

    private final AtomicReference<Map<String, Object>> latestWeather =
            new AtomicReference<>();

    public WeatherService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Fetches fresh weather data from Open-Meteo.
     * Used by both scheduled and on-demand requests.
     */
    public Map<String, Object> fetchWeather(String city) {
        return fetchWeather(city, "ON_DEMAND");
    }

    /**
     * Fetches weather data and records its execution source.
     */
    public Map<String, Object> fetchWeather(String city, String source) {

        logger.info("Weather API requested for city={}", city);

        try {
            // Step 1: Find the city's coordinates.
            Map<?, ?> locationResponse = restClient.get()
                    .uri("https://geocoding-api.open-meteo.com/v1/search"
                            + "?name={city}&count=1&language=en&format=json",
                            city)
                    .retrieve()
                    .body(Map.class);

            if (locationResponse == null
                    || !(locationResponse.get("results")
                    instanceof List<?> results)
                    || results.isEmpty()
                    || !(results.get(0) instanceof Map<?, ?> location)) {

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "City not found: " + city);
            }

            Object latitude = location.get("latitude");
            Object longitude = location.get("longitude");

            // Step 2: Fetch current weather conditions.
            Map<?, ?> weatherResponse = restClient.get()
                    .uri("https://api.open-meteo.com/v1/forecast"
                            + "?latitude={lat}&longitude={lon}"
                            + "&current=temperature_2m,relative_humidity_2m,"
                            + "apparent_temperature,weather_code,wind_speed_10m"
                            + "&timezone=auto",
                            latitude, longitude)
                    .retrieve()
                    .body(Map.class);

            if (weatherResponse == null
                    || !(weatherResponse.get("current")
                    instanceof Map<?, ?> current)) {

                throw new RestClientException(
                        "Weather response was empty");
            }

            // Build the response.
            Map<String, Object> result = Map.ofEntries(
                    Map.entry("city", location.get("name")),
                    Map.entry("country", location.get("country") == null
                            ? "Unknown" : location.get("country")),
                    Map.entry("latitude", latitude),
                    Map.entry("longitude", longitude),
                    Map.entry("timezone", weatherResponse.get("timezone")),
                    Map.entry("temperature_c",
                            current.get("temperature_2m")),
                    Map.entry("feels_like_c",
                            current.get("apparent_temperature")),
                    Map.entry("humidity_percent",
                            current.get("relative_humidity_2m")),
                    Map.entry("wind_speed_kmh",
                            current.get("wind_speed_10m")),
                    Map.entry("weather_code",
                            current.get("weather_code")),
                    Map.entry("source", "Open-Meteo")
            );

            // Update cache only after a successful API response.
            latestWeather.set(result);

            // Record successful refresh.
            meterRegistry.counter(
                    "weather_refresh",
                    "source",
                    source.toLowerCase()
            ).increment();

            logger.info("Weather API completed successfully for city={}",
                    city);

            return result;

        } catch (ResponseStatusException exception) {

            recordFailure(source);
            throw exception;

        } catch (RestClientException exception) {

            recordFailure(source);

            logger.error(
                    "Weather provider request failed for city={}",
                    city,
                    exception);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Weather provider is unavailable",
                    exception);
        }
    }

    private void recordFailure(String source) {
        meterRegistry.counter(
                "weather_refresh_failures",
                "source",
                source.toLowerCase()
        ).increment();
    }

    /**
     * Returns the latest successfully fetched weather data.
     */
    public Map<String, Object> getLatestWeather() {

        Map<String, Object> weather = latestWeather.get();

        if (weather == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Weather data is not available yet. "
                            + "Please wait for the first refresh.");
        }

        return weather;
    }

    /**
     * Refreshes weather data without requiring an API request.
     */
    public void refreshWeather(String city) {

        logger.info("Starting scheduled weather refresh for city={}",
                city);

        try {
            fetchWeather(city, "SCHEDULED");

            logger.info(
                    "Scheduled weather refresh completed for city={}",
                    city);

        } catch (Exception exception) {
            logger.error(
                    "Scheduled weather refresh failed for city={}",
                    city,
                    exception);
        }
    }
}
