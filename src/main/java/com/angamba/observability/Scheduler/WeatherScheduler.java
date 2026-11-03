
package com.angamba.observability.Scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.angamba.observability.Service.WeatherService;

@Component
public class WeatherScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(WeatherScheduler.class);

    private final WeatherService weatherService;

    @Value("${app.schedulers.weather.city:Guwahati}")
    private String city;

    public WeatherScheduler(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Scheduled(fixedDelayString =
            "${app.schedulers.weather.fixed-delay:60000}")
    public void refreshWeatherData() {

        logger.info("Weather scheduler triggered for city={}", city);

        // Refresh weather data and record it as a scheduled execution.
        weatherService.refreshWeather(city);

        logger.info("Weather scheduler execution completed for city={}", city);
    }
}
