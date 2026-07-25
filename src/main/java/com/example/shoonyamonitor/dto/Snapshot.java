package com.example.shoonyamonitor.dto;

import java.util.List;

/** Full payload broadcast to every connected browser. */
public record Snapshot(
        long serverTime,
        String marketMode,
        boolean connected,
        List<IndexQuote> indices,
        List<IntervalRanking> intervals,
        Stats stats
) {
    /** Small footer with runtime diagnostics. */
    public record Stats(int trackedScrips, long totalTicks, long uptimeSec) {
    }
}
