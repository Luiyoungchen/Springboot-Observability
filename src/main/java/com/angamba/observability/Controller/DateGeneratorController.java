
package com.angamba.observability.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angamba.observability.Service.DateGeneratorService;

@RestController
@RequestMapping("/api/date-generator")
public class DateGeneratorController {

    private final DateGeneratorService dateGeneratorService;

    public DateGeneratorController(DateGeneratorService dateGeneratorService) {
        this.dateGeneratorService = dateGeneratorService;
    }

    // Generate a new record through an API request.
    @GetMapping
    public Map<String, Object> generateDateAndUniqueNumber() {
        return dateGeneratorService.generateDateAndUniqueNumber("ON_DEMAND");
    }

    // Get total generated records.
    @GetMapping("/count")
    public Map<String, Object> getGeneratedCount() {
        return Map.of(
                "totalGenerated", dateGeneratorService.getGeneratedCount()
        );
    }

    // Get scheduled and on-demand generation counts.
    @GetMapping("/metrics")
    public Map<String, Object> getGenerationMetrics() {
        return Map.of(
                "totalGenerated", dateGeneratorService.getGeneratedCount(),
                "scheduledGenerated", dateGeneratorService.getScheduledCount(),
                "onDemandGenerated", dateGeneratorService.getOnDemandCount()
        );
    }

    // Check generator service status.
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return dateGeneratorService.getStatus();
    }
}
