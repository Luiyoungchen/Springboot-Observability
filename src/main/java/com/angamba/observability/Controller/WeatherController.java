
package com.angamba.observability.Controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WeatherController {

    private static final Logger logger =
            LoggerFactory.getLogger(WeatherController.class);

    private final RestClient restClient = RestClient.create();

    @GetMapping("/api/weather")
    public Map<String, Object> getWeather(
            @RequestParam(defaultValue = "Guwahati") String city) {

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
                    || !(locationResponse.get("results") instanceof java.util.List<?> results)
                    || results.isEmpty()
                    || !(results.get(0) instanceof Map<?, ?> location)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "City not found: " + city);
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
                    || !(weatherResponse.get("current") instanceof Map<?, ?> current)) {
                throw new RestClientException("Weather response was empty");
            }

            logger.info("Weather API completed successfully for city={}", city);

            return Map.ofEntries(
        Map.entry("city", location.get("name")),
        Map.entry("country", location.get("country") == null
                ? "Unknown" : location.get("country")),
        Map.entry("latitude", latitude),
        Map.entry("longitude", longitude),
        Map.entry("timezone", weatherResponse.get("timezone")),
        Map.entry("temperature_c", current.get("temperature_2m")),
        Map.entry("feels_like_c", current.get("apparent_temperature")),
        Map.entry("humidity_percent", current.get("relative_humidity_2m")),
        Map.entry("wind_speed_kmh", current.get("wind_speed_10m")),
        Map.entry("weather_code", current.get("weather_code")),
        Map.entry("source", "Open-Meteo")
);

        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            logger.error("Weather provider request failed for city={}", city,
                    exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Weather provider is unavailable",
                    exception);
        }
    }
}
