
package com.angamba.observability.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angamba.observability.Service.HeartbeatService;

@RestController
@RequestMapping("/api/heartbeat")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping
    public Map<String, Object> generateHeartbeat() {
        return heartbeatService.generateHeartbeat();
    }

    @GetMapping("/latest")
    public Map<String, Object> getLatestHeartbeat() {
        return heartbeatService.getLatestHeartbeat();
    }

    @GetMapping("/count")
    public Map<String, Object> getHeartbeatCount() {
        return Map.of(
                "service", "heartbeat-generator",
                "totalHeartbeats", heartbeatService.getHeartbeatCount()
        );
    }
}
