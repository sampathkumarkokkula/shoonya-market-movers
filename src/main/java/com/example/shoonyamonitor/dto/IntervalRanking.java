package com.example.shoonyamonitor.dto;

import java.util.List;

/**
 * Gainers and losers for a single time window.
 *
 * @param ready false while the app has not yet collected enough history to
 *              cover the full window (e.g. the 10m list during the first
 *              10 minutes after start)
 */
public record IntervalRanking(
        String label,
        int seconds,
        boolean ready,
        List<MovementRow> gainers,
        List<MovementRow> losers
) {
}
