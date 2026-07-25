package com.example.shoonyamonitor.dto;

/** Live snapshot of an index shown in the dashboard header. */
public record IndexQuote(
        String name,
        double ltp,
        double change,
        double pctChange,
        double open,
        double high,
        double low,
        double prevClose,
        boolean hasData
) {
}
