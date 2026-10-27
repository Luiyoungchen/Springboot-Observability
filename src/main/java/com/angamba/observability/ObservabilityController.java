
package com.angamba.observability;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservabilityController {

    private static final Logger logger =
            LoggerFactory.getLogger(ObservabilityController.class);

    @GetMapping("/")
    public Map<String, String> home() {
        logger.info("Home endpoint was accessed");

        return Map.of(
                "application", "Spring Boot Observability",
                "status", "Running",
                "message", "Application is running successfully"
        );
    }

    @GetMapping("/slow")
    public Map<String, Object> slow() throws InterruptedException {
        logger.info("Slow endpoint started");

        long startTime = System.currentTimeMillis();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }

        long duration = System.currentTimeMillis() - startTime;

        logger.info("Slow endpoint completed in {} ms", duration);

        return Map.of(
                "endpoint", "/slow",
                "status", "Completed",
                "duration_ms", duration
        );
    }
}
