
package com.angamba.observability.Scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.angamba.observability.Service.HeartbeatService;

@Component
public class HeartbeatScheduler {

    private final HeartbeatService heartbeatService;

    public HeartbeatScheduler(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @Scheduled(fixedRate = 2000)
    public void generateHeartbeat() {
    heartbeatService.generateHeartbeat("SCHEDULED");
}
}
