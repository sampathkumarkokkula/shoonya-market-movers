package com.example.shoonyamonitor.web;

import com.example.shoonyamonitor.dto.Snapshot;
import com.example.shoonyamonitor.service.MovementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convenience REST view of the current snapshot. The dashboard uses the
 * WebSocket feed; this endpoint is handy for quick checks and debugging.
 */
@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final MovementService movementService;

    public SnapshotController(MovementService movementService) {
        this.movementService = movementService;
    }

    @GetMapping("/snapshot")
    public Snapshot snapshot() {
        return movementService.buildSnapshot();
    }
}
