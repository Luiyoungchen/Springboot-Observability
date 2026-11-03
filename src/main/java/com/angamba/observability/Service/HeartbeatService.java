
package com.angamba.observability.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class HeartbeatService {

    private static final Logger logger =
            LoggerFactory.getLogger(HeartbeatService.class);

    private final AtomicLong heartbeatCounter = new AtomicLong(0);

    private final AtomicReference<Map<String, Object>> latestHeartbeat =
            new AtomicReference<>();

    private final MeterRegistry meterRegistry;

    public HeartbeatService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register heartbeat service availability.
        this.meterRegistry.gauge(
                "heartbeat_service_up",
                heartbeatCounter,
                counter -> 1.0
        );
    }

    // Default generation: on-demand API request.
    public Map<String, Object> generateHeartbeat() {
        return generateHeartbeat("ON_DEMAND");
    }

    // Generate heartbeat with its execution source.
    public Map<String, Object> generateHeartbeat(String source) {

        long sequence = heartbeatCounter.incrementAndGet();

        // Register the heartbeat generation counter.
        meterRegistry.counter(
                "heartbeat_generated",
                "source",
                source.toLowerCase()
        ).increment();

        Map<String, Object> heartbeat = Map.of(
                "service", "heartbeat-generator",
                "sequence", sequence,
                "source", source,
                "timestamp", Instant.now().toString(),
                "status", "UP"
        );

        latestHeartbeat.set(heartbeat);

        logger.info(
                "Heartbeat generated | source={} | sequence={} | timestamp={} | status=UP",
                source,
                sequence,
                heartbeat.get("timestamp")
        );

        return heartbeat;
    }

    // Retrieve the latest heartbeat.
    public Map<String, Object> getLatestHeartbeat() {

        Map<String, Object> heartbeat = latestHeartbeat.get();

        if (heartbeat == null) {
            return Map.of(
                    "service", "heartbeat-generator",
                    "status", "STARTING",
                    "message", "No heartbeat generated yet"
            );
        }

        return heartbeat;
    }

    // Retrieve the total number of generated heartbeats.
    public long getHeartbeatCount() {
        return heartbeatCounter.get();
    }
}
