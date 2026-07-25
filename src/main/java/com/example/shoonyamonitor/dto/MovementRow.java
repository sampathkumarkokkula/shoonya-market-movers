package com.example.shoonyamonitor.dto;

/**
 * One ranked row: how a scrip moved over a specific window plus a few insight
 * fields that give context beyond the raw percentage.
 */
public record MovementRow(
        String symbol,
        String exchange,
        String token,
        double ltp,
        double changeAbs,
        double changePct,
        double dayPct,
        long volume,
        double rangePosPct,
        String insight
) {
}
