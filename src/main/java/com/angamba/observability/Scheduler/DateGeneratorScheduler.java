
package com.angamba.observability.Scheduler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.angamba.observability.Service.DateGeneratorService;

@Component
public class DateGeneratorScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(DateGeneratorScheduler.class);

    private final DateGeneratorService dateGeneratorService;

    public DateGeneratorScheduler(DateGeneratorService dateGeneratorService) {
        this.dateGeneratorService = dateGeneratorService;
    }

    @Scheduled(fixedDelayString = "${app.schedulers.date-generator.fixed-delay:10000}")
    public void generateScheduledRecord() {

        Map<String, Object> result =
                dateGeneratorService.generateDateAndUniqueNumber("SCHEDULED");

        logger.info("Scheduled Date Generator executed | Result={}", result);
    }
}
