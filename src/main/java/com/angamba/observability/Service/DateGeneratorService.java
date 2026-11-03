
package com.angamba.observability.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class DateGeneratorService {

    private final MeterRegistry meterRegistry;

    public DateGeneratorService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
}

    private static final Logger logger =
            LoggerFactory.getLogger(DateGeneratorService.class);

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong generatedCount = new AtomicLong(0);
    private final AtomicLong scheduledCount = new AtomicLong(0);
    private final AtomicLong onDemandCount = new AtomicLong(0);

    // Generate a date and unique ID.
    public Map<String, Object> generateDateAndUniqueNumber() {
        return generateDateAndUniqueNumber("ON_DEMAND");
    }

    // Generate a record and identify its source.
    public Map<String, Object> generateDateAndUniqueNumber(String source) {

        long sequence = generatedCount.incrementAndGet();

        if ("SCHEDULED".equalsIgnoreCase(source)) {
            scheduledCount.incrementAndGet();
        } else {
            onDemandCount.incrementAndGet();
        }

        meterRegistry.counter(
        "date_generator_records_generated",
        "source",
        source.toLowerCase()
        ).increment();

        LocalDateTime now = LocalDateTime.now(ZONE);
        String uniqueId = UUID.randomUUID().toString();

        Map<String, Object> result = Map.of(
                "service", "date-unique-number-generator",
                "uniqueId", uniqueId,
                "sequence", sequence,
                "source", source,
                "date", now.toLocalDate().toString(),
                "time", now.toLocalTime().toString(),
                "timestamp", now.format(FORMATTER),
                "status", "SUCCESS"
        );

        logger.info(
                "Date and Unique ID generated | source={} | sequence={} | ID={} | timestamp={}",
                source, sequence, uniqueId, now.format(FORMATTER)
        );

        return result;
    }

    public long getGeneratedCount() {
        return generatedCount.get();
    }

    public long getScheduledCount() {
        return scheduledCount.get();
    }

    public long getOnDemandCount() {
        return onDemandCount.get();
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "service", "date-unique-number-generator",
                "status", "UP",
                "totalGenerated", generatedCount.get(),
                "scheduledGenerated", scheduledCount.get(),
                "onDemandGenerated", onDemandCount.get()
        );
    }
}
