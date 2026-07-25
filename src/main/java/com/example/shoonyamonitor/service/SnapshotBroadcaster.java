package com.example.shoonyamonitor.service;

import com.example.shoonyamonitor.web.MarketWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Serializes the latest {@link com.example.shoonyamonitor.dto.Snapshot} and
 * pushes it to every connected browser on a fixed cadence
 * ({@code shoonya.broadcast-interval-ms}).
 */
@Component
public class SnapshotBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBroadcaster.class);

    private final MovementService movementService;
    private final MarketWebSocketHandler handler;
    private final ObjectMapper mapper;

    public SnapshotBroadcaster(MovementService movementService,
                               MarketWebSocketHandler handler,
                               ObjectMapper mapper) {
        this.movementService = movementService;
        this.handler = handler;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${shoonya.broadcast-interval-ms:5000}")
    public void broadcast() {
        if (handler.sessionCount() == 0) {
            return; // nobody is watching; skip the work
        }
        try {
            String payload = mapper.writeValueAsString(movementService.buildSnapshot());
            handler.broadcast(payload);
        } catch (Exception e) {
            log.warn("Failed to broadcast snapshot: {}", e.getMessage());
        }
    }
}
