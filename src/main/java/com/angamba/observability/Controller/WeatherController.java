
package com.angamba.observability.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.angamba.observability.Service.WeatherService;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    // Fetch fresh weather data directly from Open-Meteo.
    @GetMapping
    public Map<String, Object> getWeather(
            @RequestParam(defaultValue = "Guwahati") String city) {

        return weatherService.fetchWeather(city);
    }

    // Retrieve the latest successfully cached weather data.
    @GetMapping("/latest")
    public Map<String, Object> getLatestWeather() {

        return weatherService.getLatestWeather();
    }
}
